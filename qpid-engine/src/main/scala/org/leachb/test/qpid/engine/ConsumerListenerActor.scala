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

import akka.actor.{Actor, ActorLogging}

class ConsumerListenerActor extends Actor with ActorLogging {
  def receive = {
    case TheMessage(topic, message) =>
      log.debug("inbound message {} to topic {}", message, topic)
  }

}
