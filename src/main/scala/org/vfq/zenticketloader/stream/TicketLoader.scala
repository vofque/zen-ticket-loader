package org.vfq.zenticketloader.stream

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import org.vfq.zenticketloader.model.ClientState
import org.vfq.zenticketloader.service.{ClientStorage, HttpClient, OutputFlowProvider}

import scala.concurrent.ExecutionContext

/**
 * TicketLoader manages ClientTicketLoader instances.
 */
class TicketLoader(
    outputFlowProvider: OutputFlowProvider,
    httpClient: HttpClient,
    clientStorage: ClientStorage,
    config: Config
)(implicit system: ActorSystem, ec: ExecutionContext) {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private val clientStates: ConcurrentMap[String, ClientState] = new ConcurrentHashMap[String, ClientState]

  def addClient(clientState: ClientState): Either[String, Unit] = {

    Option(clientStates.putIfAbsent(clientState.id, clientState)) match {
      case Some(_) =>
        Left(s"Client ${clientState.id} already exists")
      case None =>
        log.info("Creating client {}", clientState.id)
        new ClientTicketLoader(outputFlowProvider.get, httpClient, clientStorage, config).run(clientState)
        Right(())
    }
  }
}
