package io.example

import akka.actor._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import akka.pattern.{AskTimeoutException, ask}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.example.actors.RequestRouterActor
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

  lazy val requestRouterActor = system.actorOf(Props(new RequestRouterActor(eventLogService)), "RequestRouter")

  implicit val operationTimeout: Timeout = 30.seconds

  implicit def domainExceptionHandler = {
    ExceptionHandler {

      case e: AskTimeoutException =>
        complete(StatusCodes.InternalServerError -> "Request Timeout")

      case e: DomainException =>
        complete(StatusCodes.BadRequest -> e.getMessage)

      case NonFatal(ex) =>
        complete(StatusCodes.InternalServerError -> ex.getMessage)
    }
  }

  lazy val mainRoute: Route =
    pathPrefix("accounts") {
      pathPrefix(Segment) { accountId =>
        (get & pathEndOrSingleSlash) {
          complete {
            (requestRouterActor ? AccountSummaryRequest(accountId)).mapTo[ApiResponse].map(_.toString)
          }
        } ~
        put {
          complete {
            (requestRouterActor ? CreateAccountRequest(accountId)).mapTo[ApiResponse].map(_.toString)
          }
        } ~
          (get & path("balance")) {
            complete {
              (requestRouterActor ? GetBalanceRequest(accountId)).mapTo[ApiResponse].map(_.toString)
            }
          } ~
          (post & path("deposit") & parameter('amount.as[Double])) { amount =>
            complete {
              (requestRouterActor ? DepositOrWithdrawRequest(accountId, amount)).mapTo[ApiResponse].map(_.toString)
            }
          } ~
          (post & path("transfer" / Segment) & parameter('amount.as[Double])) { (receivingAccount, amount) =>
            complete {
              (requestRouterActor ? TransferRequest(accountId, receivingAccount, amount)).mapTo[ApiResponse].map(_.toString)
            }
          }
      }
    }

}
