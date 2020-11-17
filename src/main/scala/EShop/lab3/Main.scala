package EShop.lab3

import akka.actor.{ActorRef, ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App {
  val system  = ActorSystem("system")
  val manager = system.actorOf(Props[OrderManager], "orderManager")

  manager ! OrderManager.AddItem("Mleko");
  manager ! OrderManager.AddItem("Chrupki");
  manager ! OrderManager.Buy;
  manager ! OrderManager.Pay;

  Await.result(system.whenTerminated, Duration.Inf)
}
