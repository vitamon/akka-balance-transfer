package io.example.actors

import akka.actor.Actor.emptyBehavior
import akka.actor.{Actor, ActorLogging, ActorRef, Stash, Status}
import akka.pattern.pipe
import io.example.domain.AccountDomain._
import io.example.domain.AccountEntity.AccountEntry
import io.example.domain.ApiMessages._
import io.example.domain.{AccountDomain, AccountEntity}
import io.example.persist._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * Handles all operations for the given account
  */
class AccountHandlerActor(ownerId: AccountId, persistence: Persistence) extends Actor with ActorLogging with Stash {

  implicit val ec = ExecutionContext.Implicits.global

  syncState()

  def syncState() = {
    persistence.getLatestSnapshot(ownerId) pipeTo self
    context become waitingForSnapshot
  }

  def waitingForSnapshot: Receive = {

    case Some(entry: AccountEntry) =>
      context become processingRequests(entry)
      unstashAll()

    case None =>
      context become noCreatedAccountState
      unstashAll()

    case _ =>
      stash() // postpone all other events
  }

  def noCreatedAccountState: Receive = {

    case CreateAccountRequest(id) =>
      val item = AccountDomain.prepareCreateAccount(id)
      persistence.save(List(item)) pipeTo self
      context become waitingForPersistCompleted(item, sender())

    case _ =>
      log.warning("Account doesn't exist! " + ownerId)
      sender ! Status.Failure(AccountDoesNotExistException)
  }

  def processingRequests(state: AccountEntry): Receive = {

    case GetBalanceRequest(_) =>
      sender() ! BalanceResponse(ownerId, state.balance)

    case d @ DepositOrWithdrawRequest(_, update) =>

      AccountDomain.prepareDepositOrWithdraw(state, update) match {
        case Success(items) =>
          persistence.save(List(items)) pipeTo self
          context become waitingForPersistCompleted(state, sender())

        case Failure(ex) =>
          sender() ! Status.Failure(ex)
      }

    case d @ TransferRequest(_, receiverId, amount) =>

      if (AccountDomain.isValidUpdate(state.balance, -amount)) {
        context.parent ! TransferReceiverRequest(receiverId, d, state) // receiver account will complete the transaction
        context become waitingForPersistCompleted(state, sender())
      } else {
        sender ! Status.Failure(NotEnoughFundsException)
      }

    case d @ TransferReceiverRequest(_, request, sourceAccountState) =>

      AccountDomain.prepareTransfer(sourceAccountState, state, request.amount) match {
        case Success(items) =>
          persistence.save(items) pipeTo self
          context become waitingForTransferPersisted(d, sender(), state)

        case Failure(ex) =>
          sender() ! Status.Failure(ex)
      }

    case AccountSummaryRequest(_) =>
      val sndr = sender()

      retrieveTheChainOfOperations(ownerId, state.previousTransactionId, List(state)).map { lst =>

        val result = if (lst.head.operation != AccountEntity.CreateAccount) {
          log.error("The chain of operations is broken" + lst)
          AccountSummaryResponse("Warning! The chain of operations is broken\n" + lst.map(_.asString).mkString("\n"))
        } else {
          AccountSummaryResponse(lst.map(_.asString).mkString("\n"))
        }
        sndr ! result

      }.recover {
        case NonFatal(ex) =>
          sndr ! Status.Failure(ex)
      }

    case other:ApiRequest =>
      sender() ! Status.Failure(GenericDomainException("Unknown or Not Permitted Operation " + other.toString))
  }

  def waitingForPersistCompleted(oldState: AccountEntry, client: ActorRef): Receive = {

    case PersistSuccess(events) =>
      val newState = events.find(_.account == ownerId).get
      client ! BalanceResponse(newState.account, newState.balance)
      context become processingRequests(newState)
      unstashAll()

    case e: PersistFailure =>
      log.debug("Transfer failure " + e.message)
      client ! Status.Failure(e)
      context become processingRequests(oldState)
      unstashAll()

    // if the receiving account doesn't exist
    case ex: Status.Failure =>
      log.debug("Status.Failure " + ex)
      client ! ex
      context become processingRequests(oldState)
      unstashAll()

    case _ =>
      stash()
  }

  def waitingForTransferPersisted(d: TransferReceiverRequest, sourceAccount: ActorRef,
    oldState: AccountEntry): Receive = {
    case e @ PersistSuccess(events) =>
      sourceAccount ! e
      val newState = events.find(_.account == ownerId).get
      context become processingRequests(newState)
      unstashAll()

    case e: PersistFailure =>
      log.debug("Transfer failure " + e.message)
      sourceAccount ! e
      context become processingRequests(oldState)
      unstashAll()

    case _ =>
      stash()
  }

  def retrieveTheChainOfOperations(ownerId: String, txIdOpt: Option[String],
    entries: List[AccountEntry]): Future[List[AccountEntry]] = {
    txIdOpt.fold(Future.successful(entries)) { txId =>
      persistence.getByTransactionId(ownerId, txId) flatMap {
        case None =>
          Future.successful(entries)

        case Some(olderEntry) =>
          // validate the entry integrity
          if (olderEntry.getDigest != olderEntry.id) {
            log.error("The entry was edited!")
            throw InconsistentEntryException(olderEntry)
          } else {
            retrieveTheChainOfOperations(ownerId, olderEntry.previousTransactionId, olderEntry :: entries)
          }
      }
    }
  }

  def receive = emptyBehavior

}
