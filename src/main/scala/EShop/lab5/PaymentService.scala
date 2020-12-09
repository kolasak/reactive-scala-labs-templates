package EShop.lab5

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import EShop.lab5.PaymentService._

object PaymentService {

  case object PaymentSucceeded // http status 200
  class PaymentClientError extends Exception // http statuses 400, 404
  class PaymentServerError extends Exception // http statuses 500, 408, 418

  def props(method: String, payment: ActorRef) = Props(new PaymentService(method, payment))

}

class PaymentService(method: String, payment: ActorRef) extends Actor with ActorLogging {
  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)
  private val URI  = getURI

  override def preStart(): Unit =
    http
      .singleRequest(HttpRequest(uri = URI))
      .pipeTo(self)

  override def receive: Receive = {
    case resp @ HttpResponse(StatusCodes.OK, _, _, _) =>
      payment ! PaymentSucceeded
      resp.discardEntityBytes()
      context.stop(self)
    case resp @ HttpResponse(StatusCodes.BadRequest, _, _, _) => // 400
      resp.discardEntityBytes()
      throw new PaymentClientError()
    case resp @ HttpResponse(StatusCodes.NotFound, _, _, _) => // 404
      resp.discardEntityBytes()
      throw new PaymentClientError()

    case resp @ HttpResponse(StatusCodes.ImATeapot, _, _, _) => // 408
      resp.discardEntityBytes()
      throw new PaymentServerError()
    case resp @ HttpResponse(StatusCodes.RequestTimeout, _, _, _) => // 418
      resp.discardEntityBytes()
      throw new PaymentServerError()
    case resp @ HttpResponse(StatusCodes.InternalServerError, _, _, _) => // 500
      resp.discardEntityBytes()
      throw new PaymentServerError()
    case resp @ HttpResponse(StatusCodes.ServiceUnavailable, _, _, _) => // 503
      resp.discardEntityBytes()
      throw new PaymentServerError()
  }

  private def getURI: String = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }

}
