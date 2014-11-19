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
import akka.camel.CamelExtension
import org.apache.camel.component.amqp.AMQPComponent
import org.apache.qpid.amqp_1_0.jms.impl.ConnectionFactoryImpl
import org.slf4j.bridge.SLF4JBridgeHandler
import org.springframework.jms.connection.CachingConnectionFactory

object CamelAmqpApp extends App {
  def createAmqpComponent(uri: String) = {
    val cf = ConnectionFactoryImpl.createFromURL(uri)
    val cachingCf = new CachingConnectionFactory(cf)
    cachingCf.setSessionCacheSize(5)  // number of sessions to cache
    new AMQPComponent(cachingCf)
  }

  SLF4JBridgeHandler.install()

  implicit val system = ActorSystem("camel-amqp-test")

  val camelContext = CamelExtension.get(system).context
//  camelContext.addComponent("amqp", createAmqpComponent("amqp://admin:password@localhost"))
  camelContext.addComponent("amqp", createAmqpComponent("amqp://localhost"))

  val consumer = system.actorOf(Props[ConsumerEndpointActor])
  val producer = system.actorOf(Props[ProducerActor])

  Thread.sleep(3000)

  producer ! """{ "message" : "hello world" }"""

  Thread.sleep(3000)

  producer ! """{ "message" : "hello world2" }"""

}
