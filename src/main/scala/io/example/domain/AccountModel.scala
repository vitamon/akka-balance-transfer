package io.example.domain

import io.example.domain.AccountDomain.{AccountId, Currency, TransactionId}
import io.example.domain.AccountEntity.AccountEntry
import io.example.domain.ApiMessages.NotEnoughFundsException
import java.util.UUID
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
          id = acc.getDigest,
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
      if (isValidUpdate(source.balance, -amount) && isValidUpdate(receiver.balance, amount)) {
        List(
          AccountEntry(
            id = source.getDigest,
            account = source.account,
            balance = source.balance - amount,
            amount = -amount,
            previousTransactionId = Option(source.id),
            operation = AccountEntity.Transfer,
            otherParty = Option(receiver.account)
          ),

          AccountEntry(
            id = receiver.getDigest,
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
      id = UUID.randomUUID().toString,
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
    id: TransactionId,
    account: AccountId,
    previousTransactionId: Option[TransactionId],
    balance: Currency,
    operation: TransactionType,
    amount: Currency,
    otherParty: Option[AccountId] = None, // None for deposit/withdraw
    timestamp: Long = System.nanoTime()
  ) {
    def getDigest = DigestUtils.sha1Hex(toString)

    def asString: String = s"""$timestamp|$id|$account|$operation|$amount|$balance|${otherParty.getOrElse("-")}|$previousTransactionId"""
  }

}

object ApiMessages {

  sealed trait ApiRequest {
    val originId: AccountId
  }

  case class DepositOrWithdrawRequest(originId: AccountId, amount: Currency) extends ApiRequest

  case class TransferRequest(originId: AccountId, receiverId: AccountId, amount: Currency) extends ApiRequest


  sealed trait ApiResponse

  case class BalanceResponse(id: AccountId, amount: Currency) extends ApiResponse

  case class AccountSummaryResponse(summary: String) extends ApiResponse {
    override def toString = summary
  }

  abstract class DomainException(message: String) extends RuntimeException(message) with ApiResponse

  case class GeneralDomainException(msg: String) extends DomainException(msg)

  case object NotEnoughFundsException extends DomainException("Not enough funds")

  case class AccountDoesNotExist(id: AccountId) extends DomainException("Account does not exist " + id)

  case object AccountAlreadyExists extends DomainException("Account already exists")

  case object TransactionFailureException extends DomainException("Transaction failure, please retry later")

  case class InconsistentEntryException(
    entry: AccountEntry) extends DomainException(s"The entry with id = ${entry.id} was changed since it was saved: " + entry.toString)

}


