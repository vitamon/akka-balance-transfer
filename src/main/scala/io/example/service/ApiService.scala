package io.example.service

import com.typesafe.scalalogging.LazyLogging
import io.example.domain.AccountDomain._
import io.example.domain.AccountEntity
import io.example.domain.AccountEntity.AccountEntry
import io.example.domain.ApiMessages._
import io.example.persist._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ApiService(eventLogService: Persistence) extends LazyLogging {

  implicit val ec = ExecutionContext.Implicits.global

  def createAccount(accountId: AccountId): Future[ApiResponse] = {

    eventLogService.getLatestEntry(accountId).flatMap {
      case None =>
        val item = prepareCreateAccount(accountId)

        eventLogService.save(List(item)) map {
          case PersistSuccess(events) =>
            BalanceResponse(events.head.account, events.head.balance)

          case PersistFailure(ex: DuplicateKeyException) =>
            throw AccountAlreadyExists

          case PersistFailure(_) =>
            throw TransactionFailureException
        }

      case Some(_) =>
        throw AccountAlreadyExists

    }
  }

  def balance(accountId: AccountId): Future[ApiResponse] = {
    eventLogService.getLatestEntry(accountId).map {
      case None =>
        throw AccountDoesNotExist(accountId)

      case Some(state) =>
        BalanceResponse(state.account, state.balance)
    }
  }

  def deposit(e: DepositOrWithdrawRequest): Future[ApiResponse] = {
    eventLogService.getLatestEntry(e.originId).flatMap {
      case None =>
        throw AccountDoesNotExist(e.originId)

      case Some(state) =>
        prepareDepositOrWithdraw(state, e.amount) match {
          case Success(item) =>
            eventLogService.save(List(item)) flatMap {
              case PersistSuccess(events) =>
                Future.successful(BalanceResponse(events.head.account, events.head.balance))

              case PersistFailure(ex: DuplicateKeyException) =>
                deposit(e) // retry

              case PersistFailure(ex) =>
                throw ex
            }

          case Failure(ex) =>
            throw ex
        }
    }
  }

  def transfer(e: TransferRequest): Future[ApiResponse] = {

    eventLogService.getLatestEntry(e.originId).flatMap {
      case None =>
        throw AccountDoesNotExist(e.originId)

      case Some(origin) =>

        eventLogService.getLatestEntry(e.receiverId).flatMap {
          case None =>
            throw AccountDoesNotExist(e.receiverId)

          case Some(receiver) =>

            if (origin.account == receiver.account || e.amount == 0.0) {
              throw GeneralDomainException("Sender and receiver are equal, or amount is zero")
            } else {
              prepareTransfer(origin, receiver, e.amount) match {
                case Success(items) =>

                  eventLogService.save(items).flatMap {
                    case PersistSuccess(events) =>
                      val newState = events.find(_.account == origin.account).get
                      Future.successful(BalanceResponse(newState.account, newState.balance))

                    case PersistFailure(ex: DuplicateKeyException) =>
                      logger.debug("Entry was changed, retrying:" + e)
                      transfer(e) // retry

                    case PersistFailure(ex) =>
                      throw ex
                  }

                case Failure(ex) =>
                  throw ex
              }
            }
        }
    }
  }

  def summary(accountId: AccountId): Future[ApiResponse] = {
    eventLogService.getLatestEntry(accountId).flatMap {
      case None =>
        throw AccountDoesNotExist(accountId)

      case Some(state) =>
        retrieveTheChainOfOperations(accountId, state, List(state)).map { lst =>
          if (lst.head.operation != AccountEntity.CreateAccount) {
            logger.error("The chain of operations is broken" + lst)
            AccountSummaryResponse("Warning! The chain of operations is broken\n" + lst.map(_.asString).mkString("\n"))
          } else {
            AccountSummaryResponse(lst.map(_.asString).mkString("\n"))
          }
        }
    }
  }

  def retrieveTheChainOfOperations(ownerId: String, entry: AccountEntry,
    acc: List[AccountEntry]): Future[List[AccountEntry]] = {

    entry.previousTransactionId.fold(Future.successful(acc)) { txId =>
      eventLogService.getByTransactionId(ownerId, txId).flatMap {
        case None =>
          Future.successful(acc)

        case Some(olderEntry) =>
          // validate the entry integrity
          if (olderEntry.getDigest != entry.id) {
            logger.error("The entry was edited!")
            throw InconsistentEntryException(olderEntry)
          } else {
            retrieveTheChainOfOperations(ownerId, olderEntry, olderEntry :: acc)
          }
      }.recover {
        case NonFatal(ex) =>
          logger.error(s"Unable to read entry $ownerId, $txId")
          throw GeneralDomainException(s"Unable to read entry $ownerId, $txId")
      }
    }
  }

}
