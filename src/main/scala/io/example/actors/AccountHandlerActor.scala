package io.example.actors

import akka.actor.{Actor, ActorLogging}
import io.example.domain.AccountDomain.AccountId

/**
  * Handles all operations for the given account
  * Kills itself if there was no operations for long time
  */
class AccountHandlerActor(id: AccountId) extends Actor with ActorLogging {
  override def receive: Receive = {

  }
}
