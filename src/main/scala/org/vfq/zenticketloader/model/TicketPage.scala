package org.vfq.zenticketloader.model

import io.circe.Decoder

/**
 * Page of tickets returned from Zendesk.
 */
case class TicketPage(tickets: Seq[Ticket], afterCursor: Option[String])

object TicketPage {

  val empty: TicketPage = TicketPage(Seq.empty, None)

  implicit val decodeTicketsPage: Decoder[TicketPage] = {
    Decoder.forProduct2(
      "tickets",
      "after_cursor"
    )(TicketPage(_, _))
  }
}