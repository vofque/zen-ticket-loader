package org.vfq.zenticketloader.model

import java.time.Instant
import java.time.format.DateTimeFormatter

import io.circe.Decoder

import scala.util.Try

/**
 * Ticket with a limited set of fields.
 */
case class Ticket(id: Long, subject: String, description: String, createdAt: Instant, updatedAt: Instant) {

  override def toString: String = {
    s"Ticket $id | subject: $subject, description: $description, created: $createdAt, updated: $updatedAt"
  }
}

object Ticket {

  implicit val decodeInstant: Decoder[Instant] = Decoder.decodeString.emapTry { str =>
    Try(Instant.from(DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(str)))
  }

  implicit val decodeTicket: Decoder[Ticket] = {
    Decoder.forProduct5(
      "id",
      "subject",
      "description",
      "created_at",
      "updated_at"
    )(Ticket(_, _, _, _, _))
  }
}
