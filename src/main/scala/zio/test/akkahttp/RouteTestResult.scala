package zio.test.akkahttp

import akka.http.expose.ExposedRouteTest
import akka.http.scaladsl.model.headers.Upgrade
import akka.http.scaladsl.model.{
  AttributeKey, ContentType, HttpCharset, HttpEntity, HttpHeader, HttpProtocol, HttpResponse, MediaType, ResponseEntity,
  StatusCode, StatusCodes,
}
import akka.http.scaladsl.server.Rejection
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromResponseUnmarshaller, Unmarshal}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import zio._
import zio.test.akkahttp.RouteTest.Environment
import zio.test.akkahttp.RouteTestResult.Completed

import scala.collection.immutable
import scala.reflect.ClassTag

sealed trait RouteTestResult {
  def rejected: Option[immutable.Seq[Rejection]]
  def isTimeout: Boolean
  def handled: Option[Completed]
}

object RouteTestResult {
  case object TimeoutError
  type TimeoutError = TimeoutError.type

  sealed trait Lazy extends RouteTestResult {
    def toEager: IO[TimeoutError, Eager]
    def handled: Option[LazyCompleted]
  }
  sealed trait Eager extends RouteTestResult {
    def handled: Option[EagerCompleted]
  }

  final case class Rejected(rejections: immutable.Seq[Rejection]) extends Lazy with Eager {
    def toEager: UIO[Rejected] = ZIO.succeed(this)

    def rejected: Option[immutable.Seq[Rejection]] = Some(rejections)
    def isTimeout: Boolean                         = false
    def handled: Option[Nothing]                   = None
  }

  case object Timeout extends Lazy with Eager {
    def toEager: UIO[Timeout.type] = ZIO.succeed(this)

    def rejected: Option[immutable.Seq[Rejection]] = None
    def isTimeout: Boolean                         = true
    def handled: Option[Nothing]                   = None
  }

  sealed trait Completed {
    protected[akkahttp] def rawResponse: HttpResponse
    def status: StatusCode                  = rawResponse.status
    val headers: immutable.Seq[HttpHeader]  = rawResponse.headers
    val attributes: Map[AttributeKey[_], _] = rawResponse.attributes
    val protocol: HttpProtocol              = rawResponse.protocol
    def rejected: Option[Nothing]           = None
    def isTimeout: Boolean                  = false

    def header[T >: Null <: HttpHeader : ClassTag]: Option[T] = rawResponse.header[T]
    def header(name: String): Option[HttpHeader]              = rawResponse.headers.find(_.is(name.toLowerCase))

    def isWebSocketUpgrade: Boolean =
      status ==
        StatusCodes.SwitchingProtocols && header[Upgrade].exists(_.hasWebSocket)

    /** Check that the received response is a WebSocket upgrade response and extracts the chosen subprotocol.
      */
    def webSocketUpgradeProtocol: Option[String] = ExposedRouteTest.webSocketUpgradeWithProtocol(this)
  }

  final class LazyCompleted(
      protected[akkahttp] val rawResponse: HttpResponse,
      val freshEntity: IO[TimeoutError, ResponseEntity],
      val response: IO[TimeoutError, HttpResponse],
      val chunks: IO[TimeoutError, Option[immutable.Seq[HttpEntity.ChunkStreamPart]]],
    )(implicit materializer: Materializer)
      extends Lazy
      with Completed {
    def handled: Option[LazyCompleted] = Some(this)

    def entityAs[T : FromEntityUnmarshaller : ClassTag]: IO[TimeoutError, Either[Throwable, T]] =
      freshEntity.flatMap(entity => ZIO.fromFuture(implicit ec => Unmarshal(entity).to[T]).either)

    def responseAs[T : FromResponseUnmarshaller : ClassTag]: IO[TimeoutError, Either[Throwable, T]] =
      response.flatMap(response => ZIO.fromFuture(implicit ec => Unmarshal(response).to[T]).either)

    def toEager: IO[TimeoutError, EagerCompleted] =
      for {
        runtime <- ZIO.runtime[Any]
        entry   <- freshEntity
        resp    <- response
        ch      <- chunks
      } yield new EagerCompleted(entity = entry, response = resp, chunks = ch, runtime = runtime)
  }

  object LazyCompleted {

    private def awaitAllElements[T](data: Source[T, _]) =
      for {
        timeout <- ZIO.service[RouteTest.Config].map(_.timeout)
        mat     <- ZIO.service[Materializer]
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

    private def getChunks(entity: HttpEntity) =
      entity match {
        case HttpEntity.Chunked(_, chunks) => awaitAllElements(chunks).map(Some(_))
        case _                             => ZIO.none
      }

    def make(response: HttpResponse): URIO[Environment, LazyCompleted] =
      for {
        environment  <- ZIO.environment[Environment]
        freshEntityR <- freshEntityEff(response)
        freshEntity = freshEntityR.provide(environment)
      } yield new LazyCompleted(
        response,
        freshEntity,
        freshEntity.map(response.withEntity),
        freshEntity.flatMap(getChunks).provide(environment),
      )(environment.get[Materializer])
  }

  final class EagerCompleted(
      val entity: ResponseEntity,
      val response: HttpResponse,
      val chunks: Option[immutable.Seq[HttpEntity.ChunkStreamPart]],
      runtime: Runtime[Any],
    )(implicit materializer: Materializer)
      extends Eager
      with Completed {
    protected[akkahttp] def rawResponse: HttpResponse = response

    def handled: Option[EagerCompleted] = Some(this)

    def entityAs[T : FromEntityUnmarshaller : ClassTag]: Either[Throwable, T] =
      // The entry is already in memory. Run here is not so bad.
      runtime.unsafeRun(ZIO.fromFuture(implicit ec => Unmarshal(entity).to[T]).either)

    def responseAs[T : FromResponseUnmarshaller : ClassTag]: Either[Throwable, T] =
      // The response is already in memory. Run here is not so bad.
      runtime.unsafeRun(ZIO.fromFuture(implicit ec => Unmarshal(response).to[T]).either)

    def contentType: ContentType = entity.contentType

    def mediaType: MediaType = contentType.mediaType

    def charset: Option[HttpCharset] = contentType.charsetOption

    def closingExtension: Option[String] =
      chunks.flatMap {
        _.lastOption match {
          case Some(HttpEntity.LastChunk(extension, _)) => Some(extension)
          case _                                        => None
        }
      }

    def trailer: Option[immutable.Seq[HttpHeader]] =
      chunks.flatMap {
        _.lastOption match {
          case Some(HttpEntity.LastChunk(_, trailer)) => Some(trailer)
          case _                                      => None
        }
      }
  }

}
