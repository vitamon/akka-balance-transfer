package io.example.domain

import io.example.domain.AccountDomain.{AccountId, Currency, TransactionId}
import io.example.domain.AccountEntity.AccountEntry
import io.example.domain.ApiMessages.NotEnoughFundsException
import org.apache.commons.codec.digest.DigestUtils
import scala.util.Try


object AccountDomain {

  type AccountId = String

  type Currency = BigDecimal

  type TransactionId = String

  def isValidUpdate(balance: Currency, updateAmount: Currency): Boolean = {
    balance + updateAmount >= 0
  }

  def prepareDepositOrWithdraw(acc: AccountEntry, amount: Currency): Try[AccountEntry] = {
    Try {
      if (isValidUpdate(acc.balance, amount)) {
        AccountEntry(
          account = acc.account,
          balance = acc.balance + amount,
          amount = amount,
          previousTransactionId = Option(acc.id),
          operation = AccountEntity.DepositOrWithdraw
        )
      } else {
        throw NotEnoughFundsException
      }
    }
  }

  def prepareTransfer(source: AccountEntry, receiver: AccountEntry,
    amount: Currency): Try[List[AccountEntry]] = {
    Try {
      if (isValidUpdate(source.balance, -amount)) {
        List(
          AccountEntry(
            account = source.account,
            balance = source.balance - amount,
            amount = -amount,
            previousTransactionId = Option(source.id),
            operation = AccountEntity.Transfer,
            otherParty = Option(receiver.account)
          ),

          AccountEntry(
            account = receiver.account,
            balance = receiver.balance + amount,
            amount = amount,
            previousTransactionId = Option(receiver.id),
            operation = AccountEntity.Transfer,
            otherParty = Option(source.account)
          )
        )
      } else {
        throw NotEnoughFundsException
      }
    }
  }

  def prepareCreateAccount(ownerId: AccountId): AccountEntry = {
    AccountEntry(
      account = ownerId,
      balance = 0,
      amount = 0,
      previousTransactionId = None,
      operation = AccountEntity.CreateAccount
    )
  }
}


object AccountEntity {

  sealed trait TransactionType

  case object CreateAccount extends TransactionType

  case object DepositOrWithdraw extends TransactionType

  case object Transfer extends TransactionType

  case class AccountEntry(
    account: AccountId,
    previousTransactionId: Option[TransactionId],
    balance: Currency,
    operation: TransactionType,
    amount: Currency,
    otherParty: Option[AccountId] = None, // None for deposit/withdraw
    timestamp: Long = System.nanoTime()
  ) {
    val id = getDigest

    def getDigest = DigestUtils.sha1Hex(toString)

    def asString: String = s"""$timestamp|$id|$account|$operation|$amount|$balance|${otherParty.getOrElse("-")}|$previousTransactionId"""
  }

}

object ApiMessages {

  sealed trait ApiRequest {
    val ownerId: AccountId
  }

  case class CreateAccountRequest(ownerId: AccountId) extends ApiRequest

  case class AccountSummaryRequest(ownerId: AccountId) extends ApiRequest

  case class GetBalanceRequest(ownerId: AccountId) extends ApiRequest

  case class DepositOrWithdrawRequest(ownerId: AccountId, amount: Currency) extends ApiRequest

  case class TransferRequest(ownerId: AccountId, receiverId: AccountId, amount: Currency) extends ApiRequest

  case class TransferReceiverRequest(ownerId: AccountId, r: TransferRequest,
    sourceAccount: AccountEntry) extends ApiRequest


  sealed trait ApiResponse

  case class BalanceResponse(id: AccountId, amount: Currency) extends ApiResponse

  case class AccountSummaryResponse(summary: String) extends ApiResponse {
    override def toString = summary
  }

  class DomainException(message: String) extends RuntimeException(message) with ApiResponse

  case object NotEnoughFundsException extends DomainException("Not enough funds")

  case object AccountDoesNotExistException extends DomainException("Account does not exist")

  case object TransactionFailureException extends DomainException("Transaction failure, please retry later")

  case class InconsistentEntryException(
    entry: AccountEntry) extends DomainException(s"The entry with id = ${entry.id} was changed since it was saved: " + entry.toString)

  case class GenericDomainException(msg: String) extends DomainException(msg)
}


