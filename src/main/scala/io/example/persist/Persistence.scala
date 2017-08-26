package io.example.persist

import com.typesafe.scalalogging.LazyLogging
import io.example.domain.AccountDomain.AccountId
import io.example.domain.AccountEntity.AccountEntry
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.control.NonFatal

sealed trait PersistResult

case class PersistSuccess(events: List[AccountEntry]) extends PersistResult

case class PersistFailure(message: String,
  events: List[AccountEntry]) extends RuntimeException(message) with PersistResult

trait Persistence {
  // should be atomic
  def save(items: List[AccountEntry]): Future[PersistResult]

  def getLatestSnapshot(ownerId: AccountId): Future[Option[AccountEntry]]

  def getByTransactionId(ownerId: AccountId, id: String): Future[Option[AccountEntry]]
}


class InMemoryPersistenceImpl extends Persistence with LazyLogging {

  private val eventLog = new ConcurrentHashMap[AccountId, List[AccountEntry]]()

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def save(items: List[AccountEntry]): Future[PersistResult] = {
    {
      Future {
        blocking {
          items.foreach { e =>
            eventLog.put(e.account, e :: eventLog.getOrDefault(e.account, Nil))
          }
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


