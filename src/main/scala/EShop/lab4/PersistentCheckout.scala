package EShop.lab4

import EShop.lab2.CartActor
import EShop.lab3.Payment
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._
  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration     = 1.seconds

  private def checkoutTimer: Cancellable =
    context.system.scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)
  private def paymentTimer: Cancellable =
    context.system.scheduler.scheduleOnce(timerDuration, self, ExpirePayment)

  private def updateState(
    event: Event,
    maybeTimer: Option[Cancellable] = None
  ): Unit = {
    context.become(event match {
      case CheckoutStarted                => selectingDelivery(maybeTimer.getOrElse(checkoutTimer))
      case DeliveryMethodSelected(method) => selectingPaymentMethod(maybeTimer.getOrElse(checkoutTimer))
      case CheckOutClosed                 => closed
      case CheckoutCancelled              => cancelled
      case PaymentStarted(payment)        => processingPayment(maybeTimer.getOrElse(paymentTimer))
    })
  }

  def receiveCommand: Receive = {
    case StartCheckout =>
      persist(CheckoutStarted) { event =>
        updateState(event)
      }
  }

  def selectingDelivery(timer: Cancellable): Receive = {
    case CancelCheckout | ExpireCheckout =>
      timer.cancel
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
    case SelectDeliveryMethod(method) =>
      persist(DeliveryMethodSelected(method)) { event =>
        updateState(event, timer)
      }
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = {
    case CancelCheckout | ExpireCheckout =>
      timer.cancel
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
    case SelectPayment(payment) =>
      timer.cancel
      val paymentActorRef = context.system.actorOf(Props(new Payment(payment, sender, self)))
      persist(PaymentStarted(paymentActorRef)) { event =>
        updateState(event)
      }
  }

  def processingPayment(timer: Cancellable): Receive = {
    case CancelCheckout | ExpirePayment =>
      timer.cancel
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
    case ConfirmPaymentReceived =>
      timer.cancel
      persist(CheckOutClosed) { event =>
        updateState(event)
      }
  }

  def cancelled: Receive = {
    case _ => context stop self
  }

  def closed: Receive = {
    case _ => context stop self
  }

  override def receiveRecover: Receive = {
    case (event: Event)                     => updateState(event)
    case (event: Event, timer: Cancellable) => updateState(event, Option(timer))
  }
}
