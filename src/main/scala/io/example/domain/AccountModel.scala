package io.example.domain

import io.example.domain.AccountDomain.AccountId


object AccountDomain {

  type AccountId = String

}

case class Account(id: String, balance: Double)


object AccountEvents {

  sealed trait AccountEvent {
    val id: AccountId
  }

  case class DepositRequest(id: AccountId, amount: Double) extends AccountEvent

  case class TransferRequest(id: AccountId, receiverId: AccountId, amount: Double) extends AccountEvent

  case class GetBalanceRequest(id: AccountId) extends AccountEvent


  sealed trait AccountResponse {
    val id: AccountId
  }

  case class BalanceResponse(id: AccountId, amount: Double) extends AccountResponse

  case class ErrorResponse(id: AccountId, message: String) extends AccountResponse

}


