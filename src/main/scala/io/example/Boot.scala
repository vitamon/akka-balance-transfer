package io.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.ExecutionContext

object Boot extends App with LazyLogging with HttpService {

  val config = ConfigFactory.load()

  implicit val system = ActorSystem("support-admin-server", config)
  implicit val materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = ExecutionContext.global

  val serverF = Http().bindAndHandle(mainRoute, interface = "localhost", port = 8080)

}
