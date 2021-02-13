package org.vfq.zenticketloader

import akka.actor.typed.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.vfq.zenticketloader.actor.Application

object Main {

  def main(args: Array[String]): Unit = {
    val config: Config = ConfigFactory.load()
    ActorSystem[Nothing](Application(config), "zen-actor-system")
  }
}
