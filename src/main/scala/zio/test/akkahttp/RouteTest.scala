package zio.test.akkahttp

import akka.actor.ActorSystem
import akka.http.expose.ExposedRouteTest
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Host, Upgrade}
import akka.http.scaladsl.server.{Rejection, RequestContext, RouteResult}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromResponseUnmarshaller, Unmarshal}
import akka.stream.Materializer
import zio._
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.Render.param
import zio.test.Assertion._
import zio.test._
import zio.test.akkahttp.RouteTest.{Environment, System}

import scala.collection.immutable
import scala.concurrent.Future
import scala.reflect.ClassTag

trait RouteTest extends ExposedRouteTest with MarshallingTestUtils with RequestBuilding {

  def handled(assertion: AssertionM[RouteTestResult.Completed]): AssertionM[RouteTestResult] =
    AssertionM.assertionRecM("handled")(param(assertion))(assertion) {
      case complete: RouteTestResult.Completed => ZIO.some(complete)
      case _                                   => ZIO.none
    }

  def response(assertion: AssertionM[HttpResponse]): AssertionM[RouteTestResult.Completed] =
    AssertionM.assertionRecM("response")(param(assertion))(assertion) {
      _.response.fold(_ => None, Some(_))
    }

  def responseEntity(assertion: AssertionM[HttpEntity]): AssertionM[RouteTestResult.Completed] =
    AssertionM.assertionRecM("responseEntity")(param(assertion))(assertion) {
      _.freshEntity.fold(_ => None, Some(_))
    }

  def chunks(
      assertion: AssertionM[Option[immutable.Seq[HttpEntity.ChunkStreamPart]]]
    ): AssertionM[RouteTestResult.Completed] =
    AssertionM.assertionRecM("chunks")(param(assertion))(assertion) {
      _.chunks.fold(_ => None, Some(_))
    }

  def entityAs[T : FromEntityUnmarshaller : ClassTag](
      assertion: AssertionM[Either[Throwable, T]]
    ): AssertionM[RouteTestResult.Completed] =
    AssertionM.assertionRecM(s"entityAs[${implicitly[ClassTag[T]]}]")(param(assertion))(assertion) { c =>
      implicit val mat: Materializer = c.environment.get[Materializer]
      c.freshEntity
        .flatMap(entity => ZIO.fromFuture(implicit ec => Unmarshal(entity).to[T]).either)
        .fold(_ => None, Some(_))
    }

  def responseAs[T : FromResponseUnmarshaller : ClassTag](
      assertion: AssertionM[Either[Throwable, T]]
    ): AssertionM[RouteTestResult.Completed] =
    AssertionM.assertionRecM(s"responseAs[${implicitly[ClassTag[T]]}]")(param(assertion))(assertion) { c =>
      implicit val mat: Materializer = c.environment.get[Materializer]
      c.response
        .flatMap(response => ZIO.fromFuture(implicit ec => Unmarshal(response).to[T]).either)
        .fold(_ => None, Some(_))
    }

  def contentType(assertion: Assertion[ContentType]): Assertion[HttpEntity] =
    Assertion.assertionRec("contentType")(param(assertion))(assertion)(entity => Some(entity.contentType))

  def mediaType(assertion: Assertion[MediaType]): Assertion[ContentType] =
    Assertion.assertionRec("mediaType")(param(assertion))(assertion)(contentType => Some(contentType.mediaType))

  def charset(assertion: Assertion[Option[HttpCharset]]): Assertion[ContentType] =
    Assertion.assertionRec("charset")(param(assertion))(assertion)(contentType => Some(contentType.charsetOption))

  def headers(assertion: Assertion[immutable.Seq[HttpHeader]]): Assertion[RouteTestResult.Completed] =
    Assertion.assertionRec("headers")(param(assertion))(assertion)(c => Some(c.rawResponse.headers))

  def header[T >: Null <: HttpHeader : ClassTag](
      assertion: Assertion[Option[T]]
    ): Assertion[RouteTestResult.Completed] =
    Assertion.assertionRec(s"header[{implicitly[ClassTag[T]]}]")(param(assertion))(assertion) { c =>
      Some(c.rawResponse.header[T])
    }

  def header(name: String, assertion: Assertion[Option[HttpHeader]]): Assertion[RouteTestResult.Completed] =
    Assertion.assertionRec("header")(param(name), param(assertion))(assertion) { c =>
      Some(c.rawResponse.headers.find(_.is(name.toLowerCase)))
    }

  def status(assertion: Assertion[StatusCode]): Assertion[RouteTestResult.Completed] =
    Assertion.assertionRec("status")(param(assertion))(assertion)(c => Some(c.rawResponse.status))

  def closingExtension(assertion: Assertion[Option[String]]): Assertion[immutable.Seq[HttpEntity.ChunkStreamPart]] =
    Assertion.assertionRec("closingExtension")(param(assertion))(assertion) {
      _.lastOption match {
        case Some(HttpEntity.LastChunk(extension, _)) => Some(Some(extension))
        case _                                        => None
      }
    }

  def trailer(assertion: Assertion[immutable.Seq[HttpHeader]]): Assertion[immutable.Seq[HttpEntity.ChunkStreamPart]] =
    Assertion.assertionRec("trailer")(param(assertion))(assertion) {
      _.lastOption match {
        case Some(HttpEntity.LastChunk(_, trailer)) => Some(trailer)
        case _                                      => None
      }
    }

  def rejected(assertion: Assertion[immutable.Seq[Rejection]]): Assertion[RouteTestResult] =
    Assertion.assertionRec("rejected")(param(assertion))(assertion) {
      case RouteTestResult.Rejected(rejections) => Some(rejections)
      case _                                    => None
    }

  def rejection(assertion: Assertion[Rejection]): Assertion[RouteTestResult] =
    rejected(hasSize[Rejection](equalTo(1)) && hasFirst(assertion)) ?? "rejection"

  val isWebSocketUpgrade: Assertion[RouteTestResult.Completed] =
    (status(equalTo(StatusCodes.SwitchingProtocols)) &&
      header[Upgrade](isSome[Upgrade](hasField("hasWebSocket", _.hasWebSocket, equalTo(true))))) ??
      "isWebSocketUpgrade"

  implicit class WithTransformation(request: HttpRequest) {
    def ~>(f: HttpRequest => HttpRequest): HttpRequest = f(request)
    def ~>(header: HttpHeader): HttpRequest            = ~>(addHeader(header))
  }

  implicit class WithTransformationM[R, E](request: ZIO[R, E, HttpRequest]) {
    def ~>(f: HttpRequest => HttpRequest): ZIO[R, E, HttpRequest] = request.map(f)
    def ~>(header: HttpHeader): ZIO[R, E, HttpRequest]            = ~>(addHeader(header))
  }

  implicit class WithRoute(request: HttpRequest) {
    def ~>(route: RequestContext => Future[RouteResult]): URIO[Environment with System, RouteTestResult] =
      executeRequest(request, route)
  }

  implicit class WithRouteM[R, E](request: ZIO[R, E, HttpRequest]) {
    def ~>(route: RequestContext => Future[RouteResult]): ZIO[Environment with System with R, E, RouteTestResult] =
      request.flatMap(executeRequest(_, route))
  }
}

object RouteTest {
  type RouteTestConfig = Has[Config]
  type System          = Has[ActorSystem]
  type Mat             = Has[Materializer]
  type Environment     = Clock with Mat with RouteTestConfig

  case class DefaultHostInfo(host: Host, securedConnection: Boolean)

  case class Config(
      timeout: Duration = 15.seconds,
      unmarshalTimeout: Duration = 1.second,
      marshallingTimeout: Duration = Duration.Finite(1.second.toNanos),
      routeTestTimeout: Duration = 1.second,
      defaultHost: DefaultHostInfo = DefaultHostInfo(Host("example.com"), securedConnection = false))

  val testConfig: ULayer[RouteTestConfig] = ZLayer.succeed(Config())
}
