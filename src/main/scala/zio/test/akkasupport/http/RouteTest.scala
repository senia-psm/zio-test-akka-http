package zio.test.akkasupport.http

import _root_.akka.stream.scaladsl.{Sink, Source}
import akka.actor.ActorSystem
import akka.http.expose.ExposedRouteTest
import akka.http.scaladsl.model.headers.{Host, Upgrade}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Rejection, RequestContext, RouteResult}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromResponseUnmarshaller, Unmarshal}
import akka.stream.{Materializer, SystemMaterializer}
import com.typesafe.config.{Config, ConfigFactory}
import zio.clock.Clock
import zio.duration.{Duration, _}
import zio.test.Assertion.Render.param
import zio.test.Assertion._
import zio.test._
import zio.test.akkasupport.http.RouteTest.{Mat, RouteTestConfig, System}
import zio._

import scala.collection.immutable
import scala.concurrent.Future
import scala.reflect.ClassTag

sealed trait TestRouteResult

object TestRouteResult {
  case object TimeoutError
  type TimeoutError = TimeoutError.type

  final case class Rejected(rejections: immutable.Seq[Rejection]) extends TestRouteResult
  case object Timeout                                             extends TestRouteResult
  final class Completed(
      private[http] val rawResponse: HttpResponse,
      val environment: Environment,
      val response: IO[TimeoutError, HttpResponse],
      val freshEntity: IO[TimeoutError, ResponseEntity],
      val chunks: UIO[Option[immutable.Seq[HttpEntity.ChunkStreamPart]]])
      extends TestRouteResult {
    override def toString: String = s"Completed($rawResponse)"
  }

  type Environment = Clock with Mat with RouteTestConfig with System
  object Completed {

    private def awaitAllElements[T](data: Source[T, _]) =
      for {
        timeout <- ZIO.access[RouteTestConfig](_.get.timeout)
        mat     <- ZIO.access[Has[Materializer]](_.get)
        res <- ZIO
                .fromFuture(_ => data.limit(100000).runWith(Sink.seq)(mat))
                .orDie
                .timeoutFail(TimeoutError)(timeout)

      } yield res

    private def freshEntityEff(response: HttpResponse) =
      response.entity match {
        case s: HttpEntity.Strict => ZIO.succeed(ZIO.succeed(s))

        case HttpEntity.Default(contentType, contentLength, data) =>
          awaitAllElements(data).memoize
            .map(_.map(dataChunks => HttpEntity.Default(contentType, contentLength, Source(dataChunks))))

        case HttpEntity.CloseDelimited(contentType, data) =>
          awaitAllElements(data).memoize
            .map(_.map(dataChunks => HttpEntity.CloseDelimited(contentType, Source(dataChunks))))

        case HttpEntity.Chunked(contentType, data) =>
          awaitAllElements(data).memoize.map(_.map(dataChunks => HttpEntity.Chunked(contentType, Source(dataChunks))))
      }

    private def getChunks(entity: HttpEntity) = entity match {
      case HttpEntity.Chunked(_, chunks) => awaitAllElements(chunks).map(Some(_))
      case _                             => ZIO.succeed(None)
    }

    def make(response: HttpResponse): URIO[Environment, Completed] =
      for {
        environment  <- ZIO.environment[Environment]
        freshEntityR <- freshEntityEff(response)
        freshEntity = freshEntityR.provide(environment)
      } yield new Completed(
        response,
        environment,
        freshEntity.map(response.withEntity),
        freshEntity,
        freshEntity.flatMap(getChunks).fold(_ => None, identity).provide(environment)
      )
  }
}

trait RouteTest {
  this: ExposedRouteTest =>

  lazy val testSystemFromConfig: URLayer[Config, System] =
    ZLayer
      .fromAcquireRelease(ZIO.access[Config](ActorSystem(actorSystemNameFrom(getClass), _))) { sys =>
        ZIO.fromFuture(_ => sys.terminate()).orDie
      }

  lazy val testSystem: ULayer[System] = testConfig >>> testSystemFromConfig

  private def actorSystemNameFrom(clazz: Class[_]) =
    clazz.getName
      .replace('.', '-')
      .replace('_', '-')
      .filter(_ != '$')

  def testConfigSource: String = ""

  lazy val testConfig: ULayer[Config] =
    ZLayer.succeedMany {
      val source = testConfigSource
      val config = if (source.isEmpty) ConfigFactory.empty() else ConfigFactory.parseString(source)
      config.withFallback(ConfigFactory.load())
    }

  lazy val testMaterializer: URLayer[System, Mat] = ZLayer.fromService(SystemMaterializer(_).materializer)

  def handled(assertion: Assertion[TestRouteResult.Completed]): Assertion[TestRouteResult] =
    Assertion.assertionRec("handled")(param(assertion))(assertion) {
      case complete: TestRouteResult.Completed => Some(complete)
      case _                                   => None
    }

  def response(assertion: Assertion[HttpResponse]): Assertion[TestRouteResult.Completed] =
    Assertion.assertionRecM("response")(param(assertion))(assertion) {
      _.response.fold(_ => None, Some(_))
    }

  def responseEntity(assertion: Assertion[HttpEntity]): Assertion[TestRouteResult.Completed] =
    Assertion.assertionRecM("responseEntity")(param(assertion))(assertion) {
      _.freshEntity.fold(_ => None, Some(_))
    }

  def chunks(
      assertion: Assertion[Option[immutable.Seq[HttpEntity.ChunkStreamPart]]]
    ): Assertion[TestRouteResult.Completed] =
    Assertion.assertionRecM("chunks")(param(assertion))(assertion) {
      _.chunks.map(Some(_))
    }

  def entityAs[T : FromEntityUnmarshaller : ClassTag](
      assertion: Assertion[Either[Throwable, T]]
    ): Assertion[TestRouteResult.Completed] =
    Assertion.assertionRecM(s"entityAs[${implicitly[ClassTag[T]]}]")(param(assertion))(assertion) { c =>
      implicit val mat: Materializer = c.environment.get[Materializer]
      c.freshEntity
        .flatMap(entity => ZIO.fromFuture(implicit ec => Unmarshal(entity).to[T]).either)
        .fold(_ => None, Some(_))
    }

  def responseAs[T : FromResponseUnmarshaller : ClassTag](
      assertion: Assertion[Either[Throwable, T]]
    ): Assertion[TestRouteResult.Completed] =
    Assertion.assertionRecM(s"responseAs[${implicitly[ClassTag[T]]}]")(param(assertion))(assertion) { c =>
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

  def headers(assertion: Assertion[immutable.Seq[HttpHeader]]): Assertion[TestRouteResult.Completed] =
    Assertion.assertionRec("headers")(param(assertion))(assertion)(c => Some(c.rawResponse.headers))

  def header[T >: Null <: HttpHeader : ClassTag](
      assertion: Assertion[Option[T]]
    ): Assertion[TestRouteResult.Completed] =
    Assertion.assertionRec(s"header[{implicitly[ClassTag[T]]}]")(param(assertion))(assertion) { c =>
      Some(c.rawResponse.header[T])
    }

  def header(name: String, assertion: Assertion[Option[HttpHeader]]): Assertion[TestRouteResult.Completed] =
    Assertion.assertionRec("header")(param(name), param(assertion))(assertion) { c =>
      Some(c.rawResponse.headers.find(_.is(name.toLowerCase)))
    }

  def status(assertion: Assertion[StatusCode]): Assertion[TestRouteResult.Completed] =
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

  def rejected(assertion: Assertion[immutable.Seq[Rejection]]): Assertion[TestRouteResult] =
    Assertion.assertionRec("rejected")(param(assertion))(assertion) {
      case TestRouteResult.Rejected(rejections) => Some(rejections)
      case _                                    => None
    }

  def rejection(assertion: Assertion[Rejection]): Assertion[TestRouteResult] =
    rejected(hasSize[Rejection](equalTo(1)) && hasFirst(assertion)) ?? "rejection"

  val isWebSocketUpgrade: Assertion[TestRouteResult.Completed] =
    (status(equalTo(StatusCodes.SwitchingProtocols)) &&
      header[Upgrade](isSome[Upgrade](hasField("hasWebSocket", _.hasWebSocket, equalTo(true))))) ??
      "isWebSocketUpgrade"

  implicit class WithTransformation(request: HttpRequest) {
    def ~>(f: HttpRequest => HttpRequest): HttpRequest = f(request)
  }

  implicit class WithRoute(request: HttpRequest) {
    def ~>(route: RequestContext => Future[RouteResult]): URIO[TestRouteResult.Environment, TestRouteResult] =
      executeRequest(request, route)
  }
}

object RouteTest {
  type RouteTestConfig = Has[Config]
  type System          = Has[ActorSystem]
  type Mat             = Has[Materializer]

  case class DefaultHostInfo(host: Host, securedConnection: Boolean)

  case class Config(
      timeout: Duration = 15.seconds,
      unmarshalTimeout: Duration = 1.second,
      routeTestTimeout: Duration = 1.second,
      defaultHost: DefaultHostInfo = DefaultHostInfo(Host("example.com"), securedConnection = false))
}
