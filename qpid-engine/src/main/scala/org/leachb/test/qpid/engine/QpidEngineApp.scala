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

import akka.actor.{ActorSystem, Props}

object QpidEngineApp extends App {
  implicit val system = ActorSystem("amqp-test")

  val consumer = system.actorOf(Props(classOf[QpidEngineActor], "server"))
  val listener = system.actorOf(Props[ConsumerListenerActor])

  Thread.sleep(2000)

  consumer ! Subscribe("share:share1:topic1", listener)

  Thread.sleep(2000)

  val producer = system.actorOf(Props(classOf[QpidEngineActor], "client"))

  Thread.sleep(2000)

  producer ! ("""{ "message" : "hello world" }""", "topic1")

  Thread.sleep(2000)

  producer ! ("""{ "message" : "hello world 2" }""", "topic1")
}
