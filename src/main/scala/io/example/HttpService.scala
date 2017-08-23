package io.example

import akka.actor._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.stream.Materializer
import io.example.actors.SessionRouterActor
import io.example.domain.AccountEvents._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import spray.json.{DefaultJsonProtocol, _}

trait HttpService extends DefaultJsonProtocol with Directives with SprayJsonSupport {

  implicit val system: ActorSystem
  implicit val materializer: Materializer
  implicit val ec: ExecutionContext

  lazy val sessionRouterActor = system.actorOf(Props(new SessionRouterActor()))

  implicit val operationTimeout = 30.seconds

  lazy val mainRoute: Route =
    pathPrefix("accounts") {
      path(Segment) { accountId =>

        (get & path("balance")) {
          complete {
            (sessionRouterActor ? GetBalanceRequest(accountId)).mapTo[AccountResponse].map(_.toJson)
          }
        } ~
          (post & path("deposit") & parameter('amount.as[Double])) { amount =>
            complete {
              (sessionRouterActor ? DepositRequest(accountId, amount)).mapTo[AccountResponse].map(_.toJson)
            }
          } ~
          (post & path("transfer" / Segment) & parameter('amount.as[Double])) { (receivingAccount, amount) =>
            complete {
              (sessionRouterActor ? TransferRequest(accountId, receivingAccount, amount)).mapTo[AccountResponse].map(_.toJson)
            }
          }
      }
    }

}
