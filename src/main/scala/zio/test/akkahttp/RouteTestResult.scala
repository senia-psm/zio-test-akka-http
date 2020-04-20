package zio.test.akkahttp

import _root_.akka.stream.scaladsl.{Sink, Source}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Rejection
import akka.stream.Materializer
import zio._
import RouteTest.{Environment, RouteTestConfig}

import scala.collection.immutable

sealed trait RouteTestResult

object RouteTestResult {
  case object TimeoutError
  type TimeoutError = TimeoutError.type

  final case class Rejected(rejections: immutable.Seq[Rejection]) extends RouteTestResult
  case object Timeout                                             extends RouteTestResult
  final class Completed(
      private[akkahttp] val rawResponse: HttpResponse,
      val environment: Environment,
      val response: IO[TimeoutError, HttpResponse],
      val freshEntity: IO[TimeoutError, ResponseEntity],
      val chunks: IO[TimeoutError, Option[immutable.Seq[HttpEntity.ChunkStreamPart]]])
      extends RouteTestResult {
    override def toString: String = s"Completed($rawResponse)"
  }

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
        freshEntity.flatMap(getChunks).provide(environment)
      )
  }
}
