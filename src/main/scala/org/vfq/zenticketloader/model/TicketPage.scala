package org.vfq.zenticketloader.model

import io.circe.Decoder

/**
 * Page of tickets returned from Zendesk.
 */
case class TicketPage(tickets: Seq[Ticket], afterCursor: Option[String], endOfStream: Boolean)

object TicketPage {

  implicit val decodeTicketsPage: Decoder[TicketPage] = {
    Decoder.forProduct3(
      "tickets",
      "after_cursor",
      "end_of_stream"
    )(TicketPage(_, _, _))
  }
}