package io.example.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import io.example.domain.AccountDomain.AccountId
import io.example.domain.ApiMessages.{ApiRequest, TransferRequest}
import io.example.persist.Persistence
import scala.collection.mutable

/**
  * Keeps track of existing account actors,
  * makes sure that for each account we have only single managing actor (but this is not required)
  *
  * TODO: keep track of actor's last activity time and kill the idle actors after some timeout
  */
class RequestRouterActor(eventLogService: Persistence) extends Actor with ActorLogging {

  val accounts = mutable.AnyRefMap.empty[AccountId, ActorRef]

  override def receive: Receive = {

    case Terminated(child) =>
      accounts.find { case (id, actor) => actor == child }.foreach { case (id, s) =>
        log.debug("Actor removed: " + id)
        accounts -= id
      }

    case e: ApiRequest =>
      log.debug(e.toString)

      val handler = accounts.getOrElse(e.ownerId, {
        val h = context.actorOf(Props(new AccountHandlerActor(e.ownerId, eventLogService)), "Account" + e.ownerId)
        accounts += e.ownerId -> h
        h
      })
      handler.forward(e)
  }
}
