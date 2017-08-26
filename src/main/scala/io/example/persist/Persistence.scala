package io.example.persist

import com.typesafe.scalalogging.LazyLogging
import io.example.domain.AccountDomain.AccountId
import io.example.domain.AccountEntity.AccountEntry
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.control.NonFatal

sealed trait PersistResult

case class PersistSuccess(events: List[AccountEntry]) extends PersistResult

case class PersistFailure(ex: Throwable) extends RuntimeException(ex) with PersistResult

case class DuplicateKeyException(message: String) extends RuntimeException(message)

trait Persistence {
  // should be atomic
  // should throw if the id is not unique
  def save(items: List[AccountEntry]): Future[PersistResult]

  def getLatestEntry(ownerId: AccountId): Future[Option[AccountEntry]]

  def getByTransactionId(ownerId: AccountId, id: String): Future[Option[AccountEntry]]
}


class InMemoryPersistenceImpl extends Persistence with LazyLogging {

  private val eventLog = new ConcurrentHashMap[AccountId, List[AccountEntry]]()

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def save(items: List[AccountEntry]): Future[PersistResult] = {
    Future {
      blocking {
        eventLog.synchronized {
          items.foreach { e =>
            val lst = eventLog.getOrDefault(e.account, Nil)
            if (lst.exists(_.id == e.id)) {
              throw DuplicateKeyException(s"Transaction Index ${e.id} already exists")
            }
            eventLog.put(e.account, e :: lst)
          }
        }
        PersistSuccess(items)
      }
    }.recover {
      case NonFatal(ex) =>
        PersistFailure(ex)
    }
  }

  def getLatestEntry(ownerId: AccountId): Future[Option[AccountEntry]] = {
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


