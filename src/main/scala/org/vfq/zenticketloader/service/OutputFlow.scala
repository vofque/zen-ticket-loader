package org.vfq.zenticketloader.service

import akka.NotUsed
import akka.stream.scaladsl.Flow
import org.slf4j.{Logger, LoggerFactory}
import org.vfq.zenticketloader.model.CommittableTicket

object OutputFlowProvider {

  def apply(): OutputFlowProvider = new LogOutputFlowProvider
}

/**
  * Provider of an output flow for loaded tickets.
  */
trait OutputFlowProvider {

  def get: Flow[CommittableTicket, CommittableTicket, NotUsed]
}

/**
  * Output flow which simply logs tickets.
  * In the real world a Kafka producer flow should be considered.
  */
class LogOutputFlowProvider extends OutputFlowProvider {

  val log: Logger = LoggerFactory.getLogger(getClass)

  override def get: Flow[CommittableTicket, CommittableTicket, NotUsed] = {
    Flow[CommittableTicket].map { committableTicket =>
      log.info("Output: {}", committableTicket.ticket)
      committableTicket
    }
  }
}
