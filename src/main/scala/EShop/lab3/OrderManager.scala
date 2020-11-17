package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager._
import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive

object OrderManager {

  sealed trait Command
  case class AddItem(id: String)                                               extends Command
  case class RemoveItem(id: String)                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command
  case object Buy                                                              extends Command
  case object Pay                                                              extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef)                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef)                       extends Command
  case object ConfirmPaymentReceived                                           extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager extends Actor {

  override def receive = uninitialized

  def uninitialized: Receive =
    LoggingReceive {
      case AddItem(id) =>
        val cartActor = context.system.actorOf(Props[CartActor])
        cartActor ! CartActor.AddItem(id)
        sender ! Done
        context become open(cartActor)
    }

  def open(cartActor: ActorRef): Receive =
    LoggingReceive {
      case AddItem(id) =>
        cartActor ! CartActor.AddItem(id)
        sender ! Done
      case RemoveItem(id) =>
        cartActor ! CartActor.RemoveItem(id)
        sender ! Done
      case Buy =>
        cartActor ! CartActor.StartCheckout
        context become waitingForCheckoutToStart(cartActor, sender)
    }

  def waitingForCheckoutToStart(cartActorRef: ActorRef, senderRef: ActorRef): Receive =
    LoggingReceive {
      case ConfirmCheckoutStarted(checkoutRef) if sender == cartActorRef =>
        senderRef ! Done
        context become inCheckout(checkoutRef)
    }

  def inCheckout(checkoutActorRef: ActorRef): Receive =
    LoggingReceive {
      case SelectDeliveryAndPaymentMethod(delivery, payment) =>
        checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)
        checkoutActorRef ! Checkout.SelectPayment(payment)
        context become waitingForPaymentToStart(sender)
    }

  def waitingForPaymentToStart(senderRef: ActorRef): Receive =
    LoggingReceive {
      case ConfirmPaymentStarted(paymentRef) =>
        senderRef ! Done
        context become inPayment(paymentRef)
    }

  def inPayment(paymentActorRef: ActorRef): Receive =
    LoggingReceive {
      case Pay =>
        paymentActorRef ! Payment.DoPayment
        context become waitingForPaymentToFinish(sender);
    }

  def waitingForPaymentToFinish(senderRef: ActorRef): Receive =
    LoggingReceive {
      case ConfirmPaymentReceived =>
        senderRef ! Done
        context become finished
    }

  def finished: Receive = {
    case _ => sender ! "order manager finished job"
  }
}
