/**
 * mqlight-qpid-integration-test
 *
 * Written in 2014 by Bernard Leach <leachbj@bouncycastle.org>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related
 * and neighboring rights to this software to the public domain worldwide. This software is
 * distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.leachbj.test.qpid.engine

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import org.apache.qpid.proton.Proton
import org.apache.qpid.proton.amqp.messaging.{AmqpValue, Source, Target}
import org.apache.qpid.proton.engine._
import org.apache.qpid.proton.engine.impl.TransportFactory
import org.apache.qpid.proton.message.Message
import org.apache.qpid.proton.message.impl.MessageFactoryImpl


// subscribe to the given topic with the given listener actor receiving inbound messages
case class Subscribe(topic: String, listener: ActorRef)

// the message being delivered
case class TheMessage(topic: String, message: String)

// internal message
case class WriteAck(length: Int) extends akka.io.Tcp.Event

class QpidEngineActor(clientName: String) extends Actor with ActorLogging {
  import context.system

  IO(Tcp) ! Connect(new InetSocketAddress("localhost", 5672), None, List(SO.TcpNoDelay(true)))

  private var _nextTag: Long = 1

  var receiversByTopic = collection.immutable.Map[String, (Receiver, ActorRef)]()

    // really need to create children actors so we can get them to do the work
//  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def receive = waitConnect

  def waitConnect: Receive = {
    case CommandFailed(_: Connect) =>
      log.error("connect failed")
      context.stop(self)

    case c: Connected =>
      log.debug("{} connected", self.path.name)
      sender ! Register(self)
      val connection = createConnection(c.localAddress, sender)
      context.become(connected(connection, sender))
  }

  def createConnection(localAddr: InetSocketAddress, socket: ActorRef) = {
    val c: Connection = Proton.connection()

    c.setContainer(clientName)
    c.setHostname("0.0.0.0")

    val t: Transport = TransportFactory.getDefaultTransportFactory.transport(c)
    val sasl: Sasl = t.sasl
    if (sasl != null) {
      sasl.client()
      sasl.setMechanisms("ANONYMOUS")
    }

    c.open()

    val s: Session = c.session()
    s.open()

    if (t.pending() > 0) {
      sendPending(t, socket)
    }

    (s, t)
  }

  def connected(c: (Session, Transport), socket: ActorRef): Receive = {
    case Received(data) =>
      log.debug("{} received {} bytes", self.path.name, data.length)
      hexDump(data)

      val transport = c._2
      data.copyToBuffer(transport.tail())
      transport.process()


      receiversByTopic.foreach { case (topic, (receiver, listener)) =>
        val delivery = receiver.current()
          if (delivery != null && delivery.isReadable && !delivery.isPartial) {
            val size: Int = delivery.pending
            val buffer: Array[Byte] = new Array[Byte](size)
            val read: Int = receiver.recv(buffer, 0, buffer.length)

            val message: Message = new MessageFactoryImpl().createMessage()
            message.decode(buffer, 0, size)

            listener ! TheMessage(message.getAddress, message.getBody.toString)

            receiver.advance()

            // tell the remote the message is consumed
            delivery.settle()
          }
      }

      if (transport.pending() > 0) {
        sendPending(transport, socket)
      }

    case WriteAck(wrote) =>
      log.debug("{} write complete {} bytes", self.path.name, wrote)
      val transport = c._2
      transport.pop(wrote)
      if (transport.pending() > 0) {
        sendPending(transport, socket)
      }

    case Subscribe(topic, listener) =>
      log.debug("{} subscribing to topic {}", self.path.name, topic)
      val (session, transport) = c

      val receiver: Receiver = session.receiver(topic)
      val target: Target = new Target
      target.setAddress(topic)
      receiver.setTarget(target)

      val source: Source = new Source
      source.setAddress(topic)
      receiver.setSource(source)

      receiver.open
      receiver.flow(10)   // flow control - indicates we can receive 10 frames

      receiversByTopic += topic -> (receiver, listener)

      if (transport.pending() > 0) {
        sendPending(transport, socket)
      }

    case (s: String, a: String) =>
      log.debug("sending message {} to {}", s, a)
      val (session, transport) = c
      putMessage(session, a, s)
      sendPending(transport, socket)
  }

  def sendPending(transport: Transport, socket: ActorRef) = {
    if (transport.pending() > 0) {
      val head: ByteBuffer = transport.head
      val b = ByteString(head)
      log.debug("{} sendPending size {}", self.path.name, b.length)
      hexDump(b)
      socket ! Write(b, WriteAck(b.length))
    } else {
      log.debug("nothing pending to send")
    }
  }

  def putMessage(s: Session, addr: String, msg: String) = {
    val sender: Sender = s.sender(addr)
    val target: Target = new Target
    target.setAddress(addr)
    sender.setTarget(target)

    // the C implementation does this:
    val source: Source = new Source
    source.setAddress(addr)
    sender.setSource(source)

    sender.open

    val message = new MessageFactoryImpl().createMessage()
    message.setAddress("amqp://0.0.0.0/" + addr)
    message.setBody(new AmqpValue(msg))
    message.setContentType("application/json")

    val buffer = new Array[Byte](1024)
    val len = message.encode(buffer, 0, buffer.length)

    val tag: Array[Byte] = String.valueOf(_nextTag).getBytes
    _nextTag += 1
    val delivery: Delivery = sender.delivery(tag)
    sender.send(buffer, 0, len)
    sender.advance()


    //    if (getOutgoingWindow > 0) {
    //      sender.setSenderSettleMode(SenderSettleMode.UNSETTLED)
    //      sender.setReceiverSettleMode(ReceiverSettleMode.SECOND)
    //    }
  }

  def hexDump(data: ByteString) = {
    data.grouped(16).foreach { line =>
      val sb1 = new StringBuilder
      val sb2 = new StringBuilder("    ")

      line.foreach { (x: Byte) =>
        val i = x.toInt & 0xff

        sb1.append(String.format("%02X ", new Integer(i)))

        if (i >= 32 && i <= 127) {
          sb2.append(x.toChar);
        } else {
          sb2.append(".");
        }
      }
      if (sb1.length < 48) sb1.append((" " * (48 - sb1.length)))
      println(s"${sb1}${sb2}")
    }
  }

}
