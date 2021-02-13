package org.vfq.zenticketloader.actor

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.typesafe.config.Config
import org.vfq.zenticketloader.model.ClientState
import org.vfq.zenticketloader.service.{ClientStorage, HttpClient}

import scala.concurrent.ExecutionContext

/**
 * TicketLoader manages ClientTicketLoader actors: initializes, adds, forwards requests.
 */
object TicketLoader {

  sealed trait Command
  case class InitClients(clientStates: Seq[ClientState]) extends Command
  case class AddClient(clientState: ClientState, replyTo: ActorRef[AddClientResponse]) extends Command
  case class GetLateness(id: String, replyTo: ActorRef[LatenessResponse]) extends Command
  case class AddClientResponse(result: Either[String, Unit])
  case class LatenessResponse(result: Either[String, Long])

  def apply(
      httpClient: HttpClient,
      clientStorage: ClientStorage,
      ticketProcessor: ActorRef[TicketProcessor.Command],
      config: Config
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Ticket loader started")
      implicit val ec: ExecutionContext = context.executionContext
      clientStorage.get.foreach(context.self ! InitClients(_))
      new TicketLoader(httpClient, clientStorage, ticketProcessor, config).receive(Map.empty)
    }
  }
}

class TicketLoader private (
    httpClient: HttpClient,
    clientStorage: ClientStorage,
    ticketProcessor: ActorRef[TicketProcessor.Command],
    config: Config
)(implicit ec: ExecutionContext) {

  import TicketLoader._

  private def receive(clientLoaders: Map[String, ActorRef[ClientTicketLoader.Command]]): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {

        case InitClients(clientStates) =>
          context.log.info("Initializing clients {}", clientStates.map(_.id).mkString(", "))
          val newClientLoaders: Map[String, ActorRef[ClientTicketLoader.Command]] =
            clientLoaders ++ clientStates
              .map(clientState => clientState.id -> createClientLoader(clientState, context))
              .toMap
          receive(newClientLoaders)

        case AddClient(clientState, replyTo) =>
          context.log.info("Adding client {}", clientState.id)
          clientLoaders.get(clientState.id) match {
            case Some(_) =>
              replyTo ! AddClientResponse(Left(s"Client ${clientState.id} already exists"))
              Behaviors.same
            case None =>
              val clientTicketLoader: ActorRef[ClientTicketLoader.Command] = createClientLoader(clientState, context)
              clientStorage.update(clientState)
              val newClientLoaders: Map[String, ActorRef[ClientTicketLoader.Command]] =
                clientLoaders + (clientState.id -> clientTicketLoader)
              replyTo ! AddClientResponse(Right(()))
              receive(newClientLoaders)
          }

        case GetLateness(id, replyTo) =>
          clientLoaders.get(id) match {
            case Some(clientLoader) => clientLoader ! ClientTicketLoader.GetLateness(replyTo)
            case None               => replyTo ! LatenessResponse(Left(s"Client $id not found"))
          }
          Behaviors.same
      }
    }
  }

  private def createClientLoader(
      clientState: ClientState,
      context: ActorContext[Command]
  ): ActorRef[ClientTicketLoader.Command] = {
    val clientLoader: ActorRef[ClientTicketLoader.Command] = context.spawn(
      Behaviors
        .supervise(ClientTicketLoader(clientState, httpClient, clientStorage, ticketProcessor, config))
        .onFailure[Exception](SupervisorStrategy.resume),
      s"ClientTicketLoader-${clientState.id}"
    )
    clientLoader
  }
}
