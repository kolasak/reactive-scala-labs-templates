package EShop.lab2

import EShop.lab3.OrderManager
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command
  case object GetItems                 extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props() = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer(): Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive =
    LoggingReceive {
      case AddItem(item) =>
        context become nonEmpty(Cart.empty.addItem(item), scheduleTimer())
      case GetItems =>
        sender ! Cart.empty
    }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive =
    LoggingReceive {
      case AddItem(item) =>
        timer.cancel
        context become nonEmpty(cart.addItem(item), scheduleTimer())
      case RemoveItem(item) if cart.contains(item) =>
        timer.cancel
        if (cart.size == 1)
          context become empty
        else
          context become nonEmpty(cart.removeItem(item), scheduleTimer())
      case ExpireCart =>
        context become empty
      case StartCheckout =>
        timer.cancel
        val checkoutActorRef = context.system.actorOf(Props(new Checkout(self)))
        checkoutActorRef ! Checkout.StartCheckout
        sender ! OrderManager.ConfirmCheckoutStarted(checkoutActorRef)
        context become inCheckout(cart)
      case GetItems =>
        sender ! cart
    }

  def inCheckout(cart: Cart): Receive =
    LoggingReceive {
      case ConfirmCheckoutCancelled =>
        context become nonEmpty(cart, scheduleTimer())
      case ConfirmCheckoutClosed =>
        context become empty
    }

}
