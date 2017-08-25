package io.example

import io.example.domain.AccountEntity.AccountEntry
import io.example.helpers.ApiHelpers
import io.example.persist.{InMemoryPersistenceImpl, PersistFailure, PersistResult}
import java.io.IOException
import java.net.SocketTimeoutException
import org.specs2.mutable.Specification
import scala.concurrent.{Future, blocking}
import scala.util.control.NonFatal
import scalaj.http.Http
import specs2.utils.BeforeAfterAllStopOnError


class FailureScenariosRestApiSpec
  extends Specification with BeforeAfterAllStopOnError with ApiHelpers {

  val accountId = "1212"
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
            PersistFailure(ex.getMessage, items)
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
      createAccount(timeoutAccountId)
      createAccount(dbExceptionAccountId)

      // deposit funds
      deposit(accountId, 1)
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

  }

  override def afterAll {
    flakyServer.shutdown()
  }
}
