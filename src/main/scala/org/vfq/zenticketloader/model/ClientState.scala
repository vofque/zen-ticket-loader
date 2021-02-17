package org.vfq.zenticketloader.model

/**
 * ClientState represents current ticket loading progress for a specific client.
 * ClientState is supposed to be persisted in order to be able to recover after a failure.
 */
case class ClientState(
    id: String,
    domain: String,
    token: String,
    cursor: Option[String],
    lastUpdateTime: Option[Long]
)
