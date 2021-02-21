package org.vfq.zenticketloader.service

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import org.slf4j.{Logger, LoggerFactory}
import org.vfq.zenticketloader.model.ClientState
import org.vfq.zenticketloader.utils.Utils._

import scala.collection.mutable
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

object ClientStorage {

  def apply(): ClientStorage = new InMemoryStorage
}

/**
  * Storage for persisting current ticket loading progress by clients.
  */
trait ClientStorage {

  def get: Future[Seq[ClientState]]
  def getLateness(id: String): Future[Either[String, Long]]
  def update(clientState: ClientState): Future[Unit]
}

/**
  * Dummy storage persisting nothing.
  * In the real world a resilient external storage should be considered.
  */
class InMemoryStorage extends ClientStorage {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private val clients: mutable.Map[String, ClientState] = new ConcurrentHashMap[String, ClientState].asScala

  override def get: Future[Seq[ClientState]] = Future.successful(clients.values.toSeq)

  override def getLateness(id: String): Future[Either[String, Long]] = Future.successful(
    for {
      clientState <- clients.get(id).toEither(s"Client $id not found")
      lateness <- clientState.lastUpdateTime
        .map(Instant.now.getEpochSecond - _)
        .toEither("Information is unavailable yet")
    } yield lateness
  )

  override def update(clientState: ClientState): Future[Unit] = {
    clients += (clientState.id -> clientState)
    log.info("Updating state of client {} in storage", clientState.id)
    Future.unit
  }
}
