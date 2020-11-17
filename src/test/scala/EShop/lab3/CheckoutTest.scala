package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
  with AnyFlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import Checkout._

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {
    val cartProbe = TestProbe()
    val checkout  = TestActorRef(new Checkout(cartProbe.ref))

    checkout ! StartCheckout
    checkout ! SelectDeliveryMethod("in-store")
    checkout ! SelectPayment("cash")
    checkout ! ConfirmPaymentReceived

    cartProbe.expectMsg(CartActor.ConfirmCheckoutClosed)
  }

}
