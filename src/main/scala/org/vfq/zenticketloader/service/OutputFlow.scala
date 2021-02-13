package org.vfq.zenticketloader.service

import akka.NotUsed
import akka.stream.scaladsl.Flow
import org.slf4j.{Logger, LoggerFactory}
import org.vfq.zenticketloader.model.CommittableTicket

/**
 * Provider of an output flow for loaded tickets.
 */
object OutputFlow {

  val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(): Flow[CommittableTicket, CommittableTicket, NotUsed] = logFlow

  /**
   * Output flow which simply logs tickets.
   * In the real world a Kafka producer flow should be considered.
   */
  private def logFlow: Flow[CommittableTicket, CommittableTicket, NotUsed] = {
    Flow[CommittableTicket].map { committableTicket =>
      log.info("Output: {}", committableTicket.ticket)
      committableTicket
    }
  }
}
