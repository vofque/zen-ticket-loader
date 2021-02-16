package org.vfq.zenticketloader.stream

import java.util.concurrent.ConcurrentHashMap

import org.vfq.zenticketloader.model.Ticket

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Committer executes overall commit only when all tickets in the batch have been committed.
 */
class Committer(tickets: Seq[Ticket], commit: () => Unit) {

  private val ticketMap: mutable.Map[Int, Ticket] = new ConcurrentHashMap[Int, Ticket](
    tickets.map(t => t.hashCode -> t).toMap.asJava
  ).asScala

  def commit(ticket: Ticket): Unit = {
    ticketMap -= ticket.hashCode
    if (ticketMap.isEmpty) commit()
  }
}
