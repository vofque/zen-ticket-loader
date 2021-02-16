package org.vfq.zenticketloader

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.vfq.zenticketloader.service.{ClientStorage, HttpClient, HttpServer, OutputFlowProvider}
import org.vfq.zenticketloader.stream.TicketLoader

import scala.concurrent.ExecutionContext

object Main {

  def main(args: Array[String]): Unit = {

    val config: Config = ConfigFactory.load()

    implicit val system: ActorSystem = ActorSystem("zen-actor-system")
    implicit val ec: ExecutionContext = system.dispatcher

    val outputFlowProvider: OutputFlowProvider = OutputFlowProvider()
    val httpClient: HttpClient = HttpClient(config)
    val clientStorage: ClientStorage = ClientStorage()
    val ticketLoader: TicketLoader = new TicketLoader(outputFlowProvider, httpClient, clientStorage, config)
    val httpServer: HttpServer = HttpServer(ticketLoader, clientStorage, config)

    httpServer.run()

    clientStorage.get.foreach {
      _.foreach(ticketLoader.addClient)
    }
  }
}
