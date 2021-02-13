package org.vfq.zenticketloader.service

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.slf4j.{Logger, LoggerFactory}
import org.vfq.zenticketloader.actor.TicketLoader
import org.vfq.zenticketloader.model.ClientState

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}

object HttpServer {

  case class LatenessBody(lateness: Long)
  case class ErrorBody(error: String)

  def apply(
      ticketLoader: ActorRef[TicketLoader.Command],
      config: Config
  )(implicit system: ActorSystem[_], ec: ExecutionContext): HttpServer = new AkkaHttpServer(ticketLoader, config)
}

/**
 * Http server runner.
 */
trait HttpServer {

  def run(): Unit
}

class AkkaHttpServer(
    ticketLoader: ActorRef[TicketLoader.Command],
    config: Config
)(implicit system: ActorSystem[_], ec: ExecutionContext)
    extends HttpServer {

  import HttpServer._

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private implicit val requestTimeout: Timeout = Timeout(
    Duration(config.getString("server.request-timeout")).asInstanceOf[FiniteDuration]
  )

  private val host: String = config.getString("server.host")
  private val port: Int = config.getInt("server.port")

  private val route: Route = {
    concat(
      get {
        pathPrefix("client" / Segment / "lateness") { id =>
          val future: Future[TicketLoader.LatenessResponse] = ticketLoader.ask(TicketLoader.GetLateness(id, _))
          onSuccess(future) {
            _.result match {
              case Right(lateness) => complete(LatenessBody(lateness))
              case Left(error)     => complete(StatusCodes.BadRequest, ErrorBody(error))
            }
          }
        }
      },
      put {
        path("client") {
          entity(as[ClientState]) { clientState =>
            val future: Future[TicketLoader.AddClientResponse] = ticketLoader.ask(TicketLoader.AddClient(clientState, _))
            onSuccess(future) {
              _.result match {
                case Right(_) => complete(StatusCodes.Created)
                case Left(error) => complete(StatusCodes.BadRequest, ErrorBody(error))
              }
            }
          }
        }
      }
    )
  }

  override def run(): Unit = {
    log.info(s"Running server at $host:$port")
    Http().newServerAt(host, port).bind(route)
  }
}
