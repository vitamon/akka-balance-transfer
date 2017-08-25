package io.example.helpers

import io.example.domain.AccountDomain.AccountId
import io.example.domain.AccountEntity.{AccountEntry, Transfer}
import scalaj.http.Http

trait ApiHelpers {

  val baseHttpUrl = "http://0.0.0.0:8080"

  def balanceUrl(accountId: String) = baseHttpUrl + s"/accounts/$accountId/balance"

  def getBalance(accId: String) = {
    val resp = Http(balanceUrl(accId)).asString
    assert(resp.code == 200)
    resp.body
  }

  def createAccount(accountId:String) = {
    Http(baseHttpUrl + s"/accounts/$accountId").method("PUT").asString
  }

  def deposit(accId: String, amount: Double) = {
    val resp = Http(baseHttpUrl + s"/accounts/$accId/deposit").
      param("amount", amount.toString).
      method("POST").
      asString

    assert(resp.code == 200)
  }

  def matchesTestCase(accId: AccountId, items: List[AccountEntry]) = {
    items.exists(
      it => it.account == accId && it.operation == Transfer)
  }

}
