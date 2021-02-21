package org.vfq.zenticketloader.model

case class TicketPageResponse(ticketPage: TicketPage, requestTime: Option[Long])

object TicketPageResponse {

  val empty: TicketPageResponse = TicketPageResponse(TicketPage.empty, None)
}
