package org.vfq.zenticketloader.actor

import akka.{Done, NotUsed}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueue}
import com.typesafe.config.Config
import org.slf4j.Logger
import org.vfq.zenticketloader.model.{CommittableTicket, Ticket, TicketPageResponse}

import scala.concurrent.ExecutionContext

/**
 * TicketProcessor sends batches of tickets into an output stream and commits them.
 */
object TicketProcessor {

  sealed trait Command
  case class ProcessBatch(tickets: Seq[Ticket], committer: Committer, replyTo: ActorRef[Advance]) extends Command
  case class Advance(ticketPageResponse: TicketPageResponse)

  def apply(
      outputFlow: Flow[CommittableTicket, CommittableTicket, NotUsed],
      config: Config
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Ticket processor started")
      implicit val mat: Materializer = Materializer(context)
      implicit val ec: ExecutionContext = context.executionContext

      new TicketProcessor(outputFlow, config).receive()
    }
  }
}

class TicketProcessor(
    outputFlow: Flow[CommittableTicket, CommittableTicket, NotUsed],
    config: Config
)(implicit mat: Materializer, ec: ExecutionContext) {

  import TicketProcessor._

  private val bufferSize: Int = config.getInt("processor.buffer-size")

  private val queueSource: SourceQueue[Seq[CommittableTicket]] = Source
    .queue[Seq[CommittableTicket]](bufferSize, OverflowStrategy.backpressure)
    .mapConcat(identity)
    .via(outputFlow)
    .to(Sink.foreach[CommittableTicket](_.commit()))
    .run()

  private def receive(): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      val log: Logger = context.log

      message match {
        case ProcessBatch(tickets, committer, replyTo) =>
          context.log.info("Processing {} tickets", tickets.size)
          val committableTickets: Seq[CommittableTicket] = tickets.map(CommittableTicket(_, committer))
          queueSource.offer(committableTickets).map {
            case QueueOfferResult.Enqueued    =>
            case QueueOfferResult.Dropped     => log.error(s"Dropped tickets")
            case QueueOfferResult.Failure(t)  => log.error(s"Failed to enqueue tickets: {}", t.getMessage)
            case QueueOfferResult.QueueClosed => log.error(s"Queue closed")
          }
          committer.result.foreach(replyTo ! Advance(_))
          Behaviors.same
      }
    }
  }
}
