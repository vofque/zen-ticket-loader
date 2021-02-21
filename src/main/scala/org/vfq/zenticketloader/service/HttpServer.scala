package org.vfq.zenticketloader.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.slf4j.{Logger, LoggerFactory}
import org.vfq.zenticketloader.model.ClientState
import org.vfq.zenticketloader.stream.TicketLoader

import scala.concurrent.ExecutionContext

object HttpServer {

  case class LatenessBody(lateness: Long)
  case class ErrorBody(error: String)

  def apply(
      ticketLoader: TicketLoader,
      clientStorage: ClientStorage,
      config: Config
  )(implicit system: ActorSystem, ec: ExecutionContext): HttpServer = new AkkaHttpServer(ticketLoader, clientStorage, config)
}

/**
  * Http server runner.
  */
trait HttpServer {

  def run(): Unit
}

class AkkaHttpServer(
    ticketLoader: TicketLoader,
    clientStorage: ClientStorage,
    config: Config
)(implicit system: ActorSystem, ec: ExecutionContext)
    extends HttpServer {

  import HttpServer._

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private val host: String = config.getString("server.host")
  private val port: Int = config.getInt("server.port")

  private val route: Route = {
    concat(
      get {
        pathPrefix("client" / Segment / "lateness") { id =>
          onSuccess(clientStorage.getLateness(id)) {
            case Right(lateness) => complete(LatenessBody(lateness))
            case Left(error)     => complete(StatusCodes.BadRequest, ErrorBody(error))
          }
        }
      },
      put {
        path("client") {
          entity(as[ClientState]) { clientState =>
            ticketLoader.addClient(clientState) match {
              case Right(_)    => complete(StatusCodes.Created)
              case Left(error) => complete(StatusCodes.BadRequest, ErrorBody(error))
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
