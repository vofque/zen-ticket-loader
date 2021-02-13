package org.vfq.zenticketloader.actor

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.stream.scaladsl.Flow
import com.typesafe.config.Config
import org.vfq.zenticketloader.model.CommittableTicket
import org.vfq.zenticketloader.service.{ClientStorage, HttpClient, HttpServer, OutputFlow}

import scala.concurrent.ExecutionContext

object Application {

  def apply(config: Config): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      context.log.info("Application started")

      implicit val system: ActorSystem[_] = context.system
      implicit val ec: ExecutionContext = context.executionContext

      // Http client will get ticket data from Zendesk.
      val httpClient: HttpClient = HttpClient(config)
      // Client storage will be used to persist ticket loading progress by clients.
      val clientStorage: ClientStorage = ClientStorage()
      // Output flow will output tickets.
      val outputFlow: Flow[CommittableTicket, CommittableTicket, NotUsed] = OutputFlow()

      // Ticket processor will send batches of tickets into an output stream and commit them.
      val ticketProcessor: ActorRef[TicketProcessor.Command] = context.spawn(
        Behaviors
          .supervise(TicketProcessor(outputFlow, config))
          .onFailure[Exception](SupervisorStrategy.restart),
        "TicketProcessor"
      )

      // Ticket loader will manage client ticket loaders.
      // Client ticket loaders will iteratively load batches of tickets by clients
      // and send them to the ticket processor.
      val ticketLoader: ActorRef[TicketLoader.Command] = context.spawn(
        Behaviors
          .supervise(TicketLoader(httpClient, clientStorage, ticketProcessor, config))
          .onFailure[Exception](SupervisorStrategy.restart),
        "TicketLoader"
      )

      // Http server exposes API.
      val httpServer: HttpServer = HttpServer(ticketLoader, config)
      httpServer.run()

      Behaviors.ignore
    }
  }
}
