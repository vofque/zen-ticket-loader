package org.vfq.zenticketloader.stream

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueue}
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import org.vfq.zenticketloader.model.{ClientState, CommittableTicket, TicketPage, TicketPageResponse}
import org.vfq.zenticketloader.service.{ClientStorage, HttpClient}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * ClientTicketLoader loads batches of tickets for a specific client and sends them to the output flow.
 */
class ClientTicketLoader(
    outputFlow: Flow[CommittableTicket, CommittableTicket, NotUsed],
    httpClient: HttpClient,
    clientStorage: ClientStorage,
    config: Config
)(implicit system: ActorSystem, ec: ExecutionContext) {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private val BufferSize: Int = 10
  private val Parallelism: Int = 1

  private val maxRequestsPerMinute: Int = config.getInt("zendesk.max-requests-per-minute")

  val sourceQueue: SourceQueue[ClientState] = Source
    .queue[ClientState](BufferSize, OverflowStrategy.backpressure)
    .throttle(maxRequestsPerMinute, 1.minute)
    .mapAsync(Parallelism) { clientState =>
      getTicketPageResponse(clientState).map(clientState -> _)
    }
    .map {
      case (clientState, ticketPageResponse) =>
        createCommittableTickets(clientState, ticketPageResponse)
    }
    .mapConcat(identity)
    .via(outputFlow)
    .to(Sink.foreach[CommittableTicket](_.commit()))
    .run()

  def run(clientState: ClientState): Unit = {
    log.info("Running client ticket loader for client {}", clientState.id)
    commit(clientState)
  }

  private def getTicketPageResponse(clientState: ClientState): Future[TicketPageResponse] = {
    clientState.cursor
      .map { cursor =>
        log.info("Getting tickets for client {} at cursor {}", clientState.id, cursor)
        httpClient.getNextPage(cursor, clientState.token)
      }
      .getOrElse {
        val startTime: Long = clientState.lastUpdateTime.getOrElse(Instant.now.getEpochSecond)
        log.info("Getting tickets for client {} since {}", clientState.id, Instant.ofEpochSecond(startTime))
        httpClient.getFirstPage(startTime, clientState.token)
      }
      .map { ticketPageResponse =>
        log.info("Received {} tickets for client {}", ticketPageResponse.ticketPage.tickets.size, clientState.id)
        ticketPageResponse
      }
      .recover {
        case e: Exception =>
          log.error(s"Could not load tickets for client {}", clientState.id, e)
          TicketPageResponse.empty
      }
  }

  private def createCommittableTickets(
      clientState: ClientState,
      ticketPageResponse: TicketPageResponse
  ): Seq[CommittableTicket] = {
    val ticketPage: TicketPage = ticketPageResponse.ticketPage
    val nextClientState: ClientState = clientState.copy(
      cursor = ticketPage.afterCursor.orElse(clientState.cursor),
      lastUpdateTime = ticketPageResponse.requestTime.orElse(clientState.lastUpdateTime)
    )
    if (ticketPage.tickets.nonEmpty) {
      val committer: Committer = new Committer(ticketPage.tickets, () => commit(nextClientState))
      ticketPage.tickets.map(CommittableTicket(_, committer))
    } else {
      commit(nextClientState)
      Seq.empty
    }
  }

  private def commit(clientState: ClientState): Unit = {
    log.info("Committing cursor: {}, last update time: {} for client {}", clientState.cursor, clientState.lastUpdateTime, clientState.id)
    clientStorage.update(clientState)
    sourceQueue.offer(clientState)
  }
}
