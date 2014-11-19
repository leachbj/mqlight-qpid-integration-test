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

import akka.actor.ActorLogging
import akka.camel.{CamelMessage, Consumer}

class ConsumerEndpointActor extends Consumer with ActorLogging {
  // disableReplyTo=true is required as this consumer does not reply to the incoming messages
  def endpointUri = "amqp:share:share1:topic1?disableReplyTo=true"

  def receive = {
    case msg: CamelMessage =>
      log.debug("message received {}", msg.toString)
  }

}
