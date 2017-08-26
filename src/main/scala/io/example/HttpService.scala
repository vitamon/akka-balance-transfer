package io.example

import akka.actor._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import akka.pattern.AskTimeoutException
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.example.service.ApiService
import io.example.domain.ApiMessages._
import io.example.persist.Persistence
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal
import spray.json.DefaultJsonProtocol

trait HttpService extends DefaultJsonProtocol with Directives with SprayJsonSupport with LazyLogging {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val ec: ExecutionContext

  val eventLogService: Persistence

  lazy val apiService = new ApiService(eventLogService)

  implicit val operationTimeout: Timeout = 30.seconds

  implicit def domainExceptionHandler = {
    ExceptionHandler {

      case e: AskTimeoutException =>
        complete(StatusCodes.InternalServerError -> "Request Timeout")

      case e: DomainException =>
        complete(StatusCodes.BadRequest -> e.getMessage)

      case NonFatal(ex) =>
        logger.error(ex.getMessage)
        complete(StatusCodes.InternalServerError -> ex.getMessage)
    }
  }

  lazy val mainRoute: Route =
    pathPrefix("accounts") {
      pathPrefix(Segment) { accountId =>
        (get & pathEndOrSingleSlash) {
          complete {
            apiService.summary(accountId).map(_.toString)
          }
        } ~
        put {
          complete {
            apiService.createAccount(accountId).map(_.toString)
          }
        } ~
          (get & path("balance")) {
            complete {
              apiService.balance(accountId).map(_.toString)
            }
          } ~
          (post & path("deposit") & parameter('amount.as[Double])) { amount =>
            complete {
              apiService.deposit(DepositOrWithdrawRequest(accountId, amount)).map(_.toString)
            }
          } ~
          (post & path("transfer" / Segment) & parameter('amount.as[Double])) { (receivingAccount, amount) =>
            complete {
              apiService.transfer(TransferRequest(accountId, receivingAccount, amount)).map(_.toString)
            }
          }
      }
    }

}
