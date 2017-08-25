package io.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.example.persist.InMemoryPersistenceImpl
import scala.concurrent.ExecutionContext

object Boot extends App with AccountingAppServer {}

trait AccountingAppServer extends HttpService with LazyLogging {

  lazy val config = ConfigFactory.load()

  implicit val system = ActorSystem("account-transfers", config)
  implicit val materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = ExecutionContext.global

  lazy val eventLogService = new InMemoryPersistenceImpl

  val serverF = Http().bindAndHandle(mainRoute, interface = "0.0.0.0", port = 8080)

  def shutdown(): Unit = {
    serverF.flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
