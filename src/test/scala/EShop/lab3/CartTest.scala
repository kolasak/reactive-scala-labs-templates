package EShop.lab3

import EShop.lab2.{Cart, CartActor}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class CartTest
  extends TestKit(ActorSystem("CartTest"))
  with AnyFlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import CartActor._

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  val item1 = "Don Quixote"
  val item2 = "Konrad Wallenrod"

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val cart = TestActorRef(new CartActor)
    cart ! AddItem(item1)
    cart ! AddItem(item2)
    cart ! GetItems
    expectMsg(Cart(Seq(item1, item2)))
  }

  it should "be empty after adding and removing the same item" in {
    val cart = TestActorRef(new CartActor)
    cart ! AddItem(item1)
    cart ! RemoveItem(item1)
    cart ! GetItems
    expectMsg(Cart.empty)
  }

  it should "start checkout" in {
    val cart = TestActorRef(new CartActor)
    cart ! AddItem(item2)
    cart ! StartCheckout
    expectMsgType[OrderManager.ConfirmCheckoutStarted]
  }
}
