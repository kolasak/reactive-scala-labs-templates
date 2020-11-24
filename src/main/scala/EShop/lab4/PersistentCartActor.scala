package EShop.lab4

import EShop.lab2.{Cart, Checkout}
import akka.actor.{Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PersistentCartActor {

  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5.seconds

  private def scheduleTimer(): Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  override def receiveCommand: Receive = empty

  private def updateState(
    event: Event,
    timer: Option[Cancellable] = None
  ): Unit = {
    context.become(event match {
      case CartExpired | CheckoutClosed => empty
      case CheckoutCancelled(cart)      => nonEmpty(cart, scheduleTimer())
      case ItemAdded(item, cart) =>
        nonEmpty(cart.addItem(item), timer.getOrElse(scheduleTimer()))
      case CartEmptied => empty
      case ItemRemoved(item, cart) =>
        if (cart.contains(item) && cart.size == 1) empty
        else nonEmpty(cart.removeItem(item), timer.getOrElse(scheduleTimer()))
      case CheckoutStarted(checkoutRef, cart) => inCheckout(cart)
    })
  }

  def empty: Receive = {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart.empty)) { event =>
        updateState(event)
      }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
    case AddItem(item) =>
      timer.cancel
      persist(ItemAdded(item, cart)) { event =>
        updateState(event)
      }
    case RemoveItem(item) if cart.contains(item) =>
      timer.cancel
      if (cart.size == 1)
        persist(CartEmptied) { event =>
          updateState(event)
        } else
        persist(ItemRemoved(item, cart)) { event =>
          updateState(event)
        }
    case ExpireCart =>
      persist(CartExpired) { event =>
        updateState(event)
      }
    case StartCheckout =>
      timer.cancel
      val checkoutActorRef = context.system.actorOf(
        PersistentCheckout.props(self, "persistent-checkout-" + persistenceId)
      )
      persist(CheckoutStarted(checkoutActorRef, cart)) { event =>
        updateState(event)
      }
  }

  def inCheckout(cart: Cart): Receive = {
    case ConfirmCheckoutCancelled =>
      persist(CheckoutCancelled(cart)) { event =>
        updateState(event)
      }
    case ConfirmCheckoutClosed =>
      persist(CheckoutClosed) { event =>
        updateState(event)
      }
  }

  override def receiveRecover: Receive = {
    case (event: Event)                     => updateState(event)
    case (event: Event, timer: Cancellable) => updateState(event, Option(timer))
  }
}
