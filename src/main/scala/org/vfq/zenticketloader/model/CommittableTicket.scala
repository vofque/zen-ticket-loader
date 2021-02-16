package org.vfq.zenticketloader.model

import org.vfq.zenticketloader.stream.Committer

/**
 * Ticket with a corresponding committer.
 */
case class CommittableTicket(ticket: Ticket, committer: Committer) {

  def commit(): Unit = committer.commit(ticket)
}
