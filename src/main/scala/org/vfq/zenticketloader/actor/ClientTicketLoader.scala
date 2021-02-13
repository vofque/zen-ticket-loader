package org.vfq.zenticketloader.actor

import java.time.Instant

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.util.Timeout
import com.typesafe.config.Config
import org.vfq.zenticketloader.model.{ClientState, TicketPage, TicketPageResponse}
import org.vfq.zenticketloader.service.{ClientStorage, HttpClient}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scala.concurrent.duration._

/**
 * ClientTicketLoader iteratively loads batches of tickets for a specific client
 * and sends them to the TicketProcessor for processing.
 */
object ClientTicketLoader {

  sealed trait Command
  case object Load extends Command
  case class Process(ticketPageResponse: TicketPageResponse) extends Command
  case class Advance(ticketPageResponse: TicketPageResponse) extends Command
  case class RetryLoad(exception: Throwable) extends Command
  case class GetLateness(replyTo: ActorRef[TicketLoader.LatenessResponse]) extends Command

  def apply(
      clientState: ClientState,
      httpClient: HttpClient,
      clientStorage: ClientStorage,
      ticketProcessor: ActorRef[TicketProcessor.Command],
      config: Config
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Client ticket loader started for client {}", clientState.id)
      implicit val ec: ExecutionContext = context.executionContext
      context.self ! Load
      new ClientTicketLoader(httpClient, clientStorage, ticketProcessor, config).receive(clientState)
    }
  }
}

class ClientTicketLoader private (
    httpClient: HttpClient,
    clientStorage: ClientStorage,
    ticketProcessor: ActorRef[TicketProcessor.Command],
    config: Config
)(implicit ec: ExecutionContext) {

  import ClientTicketLoader._

  private val reloadDelay: FiniteDuration = Duration(config.getString("zendesk.reload-delay"))
    .asInstanceOf[FiniteDuration]

  private val retryLoadDelay: FiniteDuration = Duration(config.getString("zendesk.retry-load-delay"))
    .asInstanceOf[FiniteDuration]

  private implicit val processorTimeout: Timeout = Timeout(
    Duration(config.getString("processor.timeout")).asInstanceOf[FiniteDuration]
  )

  private def receive(clientState: ClientState): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      val self: ActorRef[Command] = context.self

      message match {

        case Load =>
          clientState.cursor
            .map { cursor =>
              context.log.info("Loading next page with cursor {} for client {}", cursor, clientState.id)
              httpClient.getNextPage(cursor, clientState.token)
            }
            .getOrElse {
              val startTime: Long = clientState.lastUpdateTime.getOrElse(Instant.now.getEpochSecond)
              context.log.info("Loading first page since {} for client {}", Instant.ofEpochSecond(startTime), clientState.id)
              httpClient.getFirstPage(startTime, clientState.token)
            }
            .onComplete {
              case Success(ticketPageResponse) =>
                self ! Process(ticketPageResponse)
              case Failure(t) =>
                self ! RetryLoad(t)
            }
          Behaviors.same

        case RetryLoad(exception: Throwable) =>
          context.log.error("Failed to process tickets {}: {}", clientState.id, exception.getMessage)
          context.scheduleOnce(retryLoadDelay, context.self, Load)
          Behaviors.same

        case Process(ticketPageResponse: TicketPageResponse) =>
          val ticketPage: TicketPage = ticketPageResponse.ticketPage
          context.log.info("Received {} tickets for client {}", ticketPage.tickets.size, clientState.id)
          if (ticketPage.tickets.nonEmpty) {
            val committer: Committer = new Committer(ticketPageResponse)
            context.ask(ticketProcessor, TicketProcessor.ProcessBatch(ticketPage.tickets, committer, _)) {
              case Success(TicketProcessor.Advance(ticketPageResponse)) => Advance(ticketPageResponse)
              case Failure(t)                                           => RetryLoad(t)
            }
            Behaviors.same
          } else {
            val newClientState: ClientState = clientState.copy(lastUpdateTime = Some(ticketPageResponse.requestTime))
            clientStorage.update(newClientState)
            context.scheduleOnce(reloadDelay, context.self, Load)
            receive(newClientState)
          }

        case Advance(ticketPageResponse: TicketPageResponse) =>
          val TicketPageResponse(ticketPage, requestTime) = ticketPageResponse
          context.log.info("Advancing with cursor: {}, time: {} for client {}", ticketPage.afterCursor, requestTime, clientState.id)
          val newClientState: ClientState = clientState.copy(
            cursor = ticketPage.afterCursor.orElse(clientState.cursor),
            lastUpdateTime = Some(requestTime)
          )
          clientStorage.update(newClientState)
          if (ticketPage.endOfStream) {
            context.log.info("End of stream reached for client {}", clientState.id)
            context.scheduleOnce(reloadDelay, context.self, Load)
          } else {
            context.log.info("End of stream not yet reached for client {}", clientState.id)
            context.self ! Load
          }
          receive(newClientState)

        case GetLateness(replyTo) =>
          val result: Either[String, Long] = clientState.lastUpdateTime
            .map(t => Right(Instant.now.getEpochSecond - t))
            .getOrElse(Left("Information is unavailable yet"))
          replyTo ! TicketLoader.LatenessResponse(result)
          Behaviors.same
      }
    }
  }
}
