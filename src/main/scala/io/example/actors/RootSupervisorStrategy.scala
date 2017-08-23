package io.example.actors

import akka.actor.SupervisorStrategy._
import akka.actor._
import com.typesafe.scalalogging.LazyLogging


class RootSupervisorStrategy extends SupervisorStrategyConfigurator with LazyLogging {
  override def create(): SupervisorStrategy = {

    OneForOneStrategy()({
      case _: ActorInitializationException => Stop
      case _: ActorKilledException => Stop
      case _: DeathPactException => Stop
      case e: Exception =>
        logger.error("Root level actor crashed, will be restarted", e)
        Restart
    })
  }

}
