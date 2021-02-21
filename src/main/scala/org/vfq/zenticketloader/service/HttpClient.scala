package org.vfq.zenticketloader.service

import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.util.ByteString
import com.typesafe.config.Config
import io.circe.parser
import org.vfq.zenticketloader.model.{TicketPage, TicketPageResponse}

import scala.concurrent.{ExecutionContext, Future}

object HttpClient {

  def apply(config: Config)(implicit system: ActorSystem, ec: ExecutionContext): HttpClient = new AkkaHttpClient(config)
}

/**
 * Http client to get ticket data from Zendesk.
 */
trait HttpClient {

  def getFirstPage(startTime: Long, domain: String, token: String): Future[TicketPageResponse]
  def getNextPage(cursor: String, domain: String, token: String): Future[TicketPageResponse]
}

class AkkaHttpClient(config: Config)(implicit system: ActorSystem, ec: ExecutionContext) extends HttpClient {

  private val url: String = config.getString("zendesk.tickets-url")

  override def getFirstPage(startTime: Long, domain: String, token: String): Future[TicketPageResponse] = {
    val uri = Uri(url.format(domain)).withQuery(Query("start_time" -> startTime.toString))
    val request: HttpRequest = pageRequest(uri, token)
    getPageResponse(request)
  }

  override def getNextPage(cursor: String, domain: String, token: String): Future[TicketPageResponse] = {
    val uri = Uri(url.format(domain)).withQuery(Query("cursor" -> cursor))
    val request: HttpRequest = pageRequest(uri, token)
    getPageResponse(request)
  }

  private def getPageResponse(
      request: HttpRequest
  )(implicit system: ActorSystem, ec: ExecutionContext): Future[TicketPageResponse] = {
    val requestTime: Long = Instant.now.getEpochSecond
    for {
      response <- Http().singleRequest(request)
      responseBody <- response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
      ticketPage <- if (response.status.isSuccess) {
        Future.fromTry(parser.decode[TicketPage](responseBody).toTry)
      } else {
        Future.failed(new Exception(s"Could not get tickets: received status ${response.status.value}"))
      }
    } yield TicketPageResponse(ticketPage, Some(requestTime))
  }

  private def pageRequest(uri: Uri, token: String): HttpRequest = {
    val headers = Seq(Authorization(OAuth2BearerToken(token)))
    HttpRequest(GET, uri).withHeaders(headers)
  }
}
