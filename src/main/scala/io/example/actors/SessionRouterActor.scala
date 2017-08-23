package io.example.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import io.example.domain.AccountDomain.AccountId
import io.example.domain.AccountEvents.AccountEvent
import scala.collection.mutable
import scala.concurrent.ExecutionContext

/**
  * Keeps track of existing account actors,
  * makes sure that for each account we have only single managing actor (but this is not required)
  */
class SessionRouterActor extends Actor with ActorLogging {

  val accounts = mutable.AnyRefMap.empty[AccountId, ActorRef]

  implicit val ec = ExecutionContext.Implicits.global

  override def receive: Receive = {

    case Terminated(child) =>
      accounts.find { case (id, actor) => actor == child }.foreach { case (id, s) =>
        log.debug("Actor removed: " + id)
        accounts -= id
      }

    case e: AccountEvent =>
      val handler = accounts.getOrElse(e.id, {
        val h = context.actorOf(Props(new AccountHandlerActor(e.id)))
        accounts += e.id -> h
        h
      })
      handler.forward(e)
  }
}
