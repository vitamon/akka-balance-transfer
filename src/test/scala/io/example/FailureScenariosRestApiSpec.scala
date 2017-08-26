package io.example

import io.example.domain.AccountEntity.AccountEntry
import io.example.helpers.ApiHelpers
import io.example.persist.{InMemoryPersistenceImpl, PersistFailure, PersistResult}
import java.io.IOException
import java.net.SocketTimeoutException
import org.specs2.mutable.Specification
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, blocking}
import scala.util.control.NonFatal
import scalaj.http.Http
import specs2.utils.BeforeAfterAllStopOnError

class FailureScenariosRestApiSpec
  extends Specification with BeforeAfterAllStopOnError with ApiHelpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  val accountId = "1212"
  val deadLockAccountId = "999"
  val timeoutAccountId = "123"
  val dbExceptionAccountId = "666"

  val flakyServer = new AccountingAppServer {
    override lazy val eventLogService = new InMemoryPersistenceImpl {
      override def save(items: List[AccountEntry]): Future[PersistResult] = {
        {
          if (matchesTestCase(timeoutAccountId, items)) {
            Future {
              blocking {
                Thread.sleep(10000)
                throw new SocketTimeoutException("transaction timed out")
              }
            }
          }
          else if (matchesTestCase(deadLockAccountId, items)) {
            Future {
              blocking {
                Thread.sleep(10) // some time for deadlock
              }
            } flatMap (_ => super.save(items))
          }
          else if (matchesTestCase(dbExceptionAccountId, items)) {
            Future {
              blocking {
                Thread.sleep(1000)
                throw new IOException("Database error occurred")
              }
            }
          }
          else {
            super.save(items)
          }
        }.recover {
          case NonFatal(ex) =>
            PersistFailure(ex)
        }
      }
    }
  }

  stopOnFail
  sequential

  "Account API" should {

    step {
      Thread.sleep(1000)
    }

    step {
      // create accounts
      createAccount(accountId)
      createAccount(deadLockAccountId)
      createAccount(timeoutAccountId)
      createAccount(dbExceptionAccountId)

      // deposit funds
      deposit(accountId, 222)
      deposit(deadLockAccountId, 333)
      deposit(timeoutAccountId, 150)
      deposit(dbExceptionAccountId, 345)
    }

    "handle db timeout" in {
      val resp = Http(baseHttpUrl + s"/accounts/$timeoutAccountId/transfer/$accountId").
        param("amount", "100").
        method("POST").
        timeout(1000, 30000).
        asString
      resp.code === 500
      resp.body === s"transaction timed out"

      getBalance(timeoutAccountId) === "BalanceResponse(123,150.0)"
    }

    "handle db exception" in {
      val resp = Http(baseHttpUrl + s"/accounts/$dbExceptionAccountId/transfer/$accountId").
        param("amount", "100").
        method("POST").
        timeout(1000, 30000).
        asString
      resp.code === 500
      resp.body === s"Database error occurred"

      getBalance(dbExceptionAccountId) === "BalanceResponse(666,345.0)"
    }

    "handle deadlock" in {

      (1 until 100) foreach { i =>
        val resp = Future {
          blocking {
            Http(baseHttpUrl + s"/accounts/$deadLockAccountId/transfer/$accountId").
              param("amount", "10").
              method("POST").
              asString
          }
        }

        val resp2 = Future {
          blocking {
            Http(baseHttpUrl + s"/accounts/$accountId/transfer/$deadLockAccountId").
              param("amount", "10").
              method("POST").
              asString
          }
        }

        val both = for {
          r1 <- resp
          r2 <- resp2
        } yield (r1.code, r2.code)

        Await.result(both, 10.seconds) === (200, 200)

        getBalance(accountId) === "BalanceResponse(1212,222.0)"
        getBalance(deadLockAccountId) === "BalanceResponse(999,333.0)"
      }
      ok
    }


  }

  override def afterAll {
    flakyServer.shutdown()
  }
}
