package org.vfq.zenticketloader.actor

import java.util.concurrent.ConcurrentHashMap

import org.vfq.zenticketloader.model.{Ticket, TicketPageResponse}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.Success

/**
 * Committer completes the underlying promise only when all tickets in the batch have been committed.
 */
class Committer(ticketPageResponse: TicketPageResponse) {

  private val ticketMap: mutable.Map[Int, Ticket] = new ConcurrentHashMap[Int, Ticket](
    ticketPageResponse.ticketPage.tickets.map(t => t.hashCode -> t).toMap.asJava
  ).asScala

  private val promise: Promise[TicketPageResponse] = Promise()

  def commit(ticket: Ticket): Unit = {
    ticketMap -= ticket.hashCode
    if (ticketMap.isEmpty) promise.complete(Success(ticketPageResponse))
  }

  val result: Future[TicketPageResponse] = promise.future
}
