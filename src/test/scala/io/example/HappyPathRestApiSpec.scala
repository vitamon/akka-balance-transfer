package io.example

import io.example.helpers.ApiHelpers
import org.specs2.mutable.Specification
import scalaj.http.Http
import specs2.utils.BeforeAfterAllStopOnError

class HappyPathRestApiSpec
  extends Specification with BeforeAfterAllStopOnError with ApiHelpers {

  val server = new AccountingAppServer {}

  val accountId = "123"
  val nonExistingAccountId = "666"
  val receivingAccountId = "987"

  stopOnFail
  sequential

  "Account API" should {

    step {
      Thread.sleep(1000)
    }

    "create accounts" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId").
        method("PUT").
        asString
      resp.code === 200

      val resp2 = Http(baseHttpUrl + s"/accounts/$receivingAccountId").
        method("PUT").
        asString

      resp2.code === 200
    }

    "fail to create existing account" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId").
        method("PUT").
        asString
      resp.code === 400
      resp.body === "Account already exists"
    }

    "get balance" in {
      getBalance(accountId) === s"BalanceResponse($accountId,0)"
    }

    "fail to get balance from non-existing account" in {
      val resp = Http(balanceUrl(nonExistingAccountId)).asString
      resp.code === 400
      resp.body === s"Account does not exist $nonExistingAccountId"
    }

    "deposit" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId/deposit").
        param("amount", "365.23").
        method("POST").
        asString

      resp.code === 200
      resp.body === s"BalanceResponse($accountId,365.23)"
    }

    "withdraw a valid sum" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId/deposit").
        param("amount", "-200").
        method("POST").
        asString

      resp.code === 200
      resp.body === s"BalanceResponse($accountId,165.23)"
    }

    "not allow to withdraw more than available" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId/deposit").
        param("amount", "-500").
        method("POST").
        asString

      resp.code === 400
      resp.body === "Not enough funds"
    }

    "transfer from one account to different" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId/transfer/$receivingAccountId").
        param("amount", "150").
        method("POST").
        asString
      resp.body === s"BalanceResponse($accountId,15.23)"

      getBalance(receivingAccountId) === s"BalanceResponse($receivingAccountId,150.0)"
    }

    "not transfer from one account to different in not enough funds" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId/transfer/$receivingAccountId").
        param("amount", "100").
        method("POST").
        asString

      resp.code === 400
      resp.body === "Not enough funds"
    }

    "transfer back" in {
      val resp = Http(baseHttpUrl + s"/accounts/$receivingAccountId/transfer/$accountId").
        param("amount", "75").
        method("POST").
        asString
      resp.body === s"BalanceResponse($receivingAccountId,75.0)"

      getBalance(accountId) === s"BalanceResponse($accountId,90.23)"
    }

    "fail to transfer to non existing account" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId/transfer/$nonExistingAccountId").
        param("amount", "10").
        method("POST").
        asString

      resp.code === 400
      resp.body === s"Account does not exist $nonExistingAccountId"

      getBalance(accountId) === s"BalanceResponse($accountId,90.23)"
    }

    "fail to transfer from non existing account" in {
      val resp = Http(baseHttpUrl + s"/accounts/$nonExistingAccountId/transfer/$accountId").
        param("amount", "10").
        method("POST").
        asString

      resp.code === 400
      resp.body === s"Account does not exist $nonExistingAccountId"

      getBalance(accountId) === s"BalanceResponse($accountId,90.23)"
    }

    "get account summary" in {
      val resp = Http(baseHttpUrl + s"/accounts/$accountId").
        asString

      println(resp.body)

      resp.code === 200
    }
  }

  override def afterAll {
    server.shutdown()
  }
}
