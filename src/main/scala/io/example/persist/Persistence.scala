package io.example.persist

import com.typesafe.scalalogging.LazyLogging
import io.example.domain.AccountDomain.AccountId
import io.example.domain.AccountEntity.AccountEntry
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.control.NonFatal

sealed trait PersistResult

case class PersistSuccess(events: List[AccountEntry]) extends PersistResult

case class PersistFailure(message: String,
  events: List[AccountEntry]) extends RuntimeException(message) with PersistResult

case class DuplicateIndexException(message: String) extends RuntimeException(message)

trait Persistence {
  // should be atomic
  // should throw if the id is not unique
  def save(items: List[AccountEntry]): Future[PersistResult]

  def getLatestSnapshot(ownerId: AccountId): Future[Option[AccountEntry]]

  def getByTransactionId(ownerId: AccountId, id: String): Future[Option[AccountEntry]]
}


class InMemoryPersistenceImpl extends Persistence with LazyLogging {

  private val eventLog = new ConcurrentHashMap[AccountId, List[AccountEntry]]()

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def save(items: List[AccountEntry]): Future[PersistResult] = {
    Future {
      blocking {
        items.foreach { e =>

          eventLog.putIfAbsent(e.account, Nil)
          eventLog.computeIfPresent(e.account, new BiFunction[String, List[AccountEntry], List[AccountEntry]] {
            override def apply(t: String, lst: List[AccountEntry]): List[AccountEntry] = {
              if (lst.exists(_.id == e.id)) {
                throw DuplicateIndexException(s"Transaction Index ${e.id} already exists")
              }
              e :: lst
            }
          })

        }
        PersistSuccess(items)
      }
    }.recover {
      case NonFatal(ex) =>
        PersistFailure(ex.getMessage, items)
    }
  }

  def getLatestSnapshot(ownerId: AccountId): Future[Option[AccountEntry]] = {
    Future {
      blocking {
        eventLog.getOrDefault(ownerId, Nil).headOption
      }
    }
  }

  def getByTransactionId(ownerId: AccountId, id: String): Future[Option[AccountEntry]] = {
    Future {
      blocking {
        eventLog.getOrDefault(ownerId, Nil).find(_.id == id)
      }
    }
  }
}


