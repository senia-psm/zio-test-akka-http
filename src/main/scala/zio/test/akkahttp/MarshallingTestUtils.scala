/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package zio.test.akkahttp

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, MediaRange}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import akka.stream.Materializer
import zio.clock.Clock
import RouteTest.{Environment, Mat, RouteTestConfig}
import zio.{RIO, ZIO}
import zio.duration._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait MarshallingTestUtils {

  private def fromFutureWithMarshalingTimeout[A](
      eff: ExecutionContext => Future[A],
      factor: Double = 1,
    ): ZIO[Clock with RouteTestConfig, Throwable, A] =
    for {
      config <- ZIO.access[RouteTestConfig](_.get)
      marshallingTimeout = config.marshallingTimeout
      res <- ZIO
               .fromFuture(eff)
               .timeoutFail(
                 new RuntimeException(s"Can't get result within marshallingTimeout $marshallingTimeout"),
               )(marshallingTimeout * factor)
               .orDie
    } yield res

  def marshal[T: ToEntityMarshaller](value: T): RIO[Environment, HttpEntity.Strict] =
    for {
      mat    <- ZIO.access[Mat](_.get)
      config <- ZIO.access[RouteTestConfig](_.get)
      marshallingTimeout = config.marshallingTimeout
      res <- fromFutureWithMarshalingTimeout(
               { implicit ec =>
                 Marshal(value)
                   .to[HttpEntity]
                   .flatMap(_.toStrict(FiniteDuration(marshallingTimeout.toNanos, TimeUnit.NANOSECONDS))(mat))
               },
               2,
             )
    } yield res

  def marshalToResponseForRequestAccepting[T: ToResponseMarshaller](
      value: T,
      mediaRanges: MediaRange*,
    ): RIO[Clock with RouteTestConfig, HttpResponse] =
    marshalToResponse(value, HttpRequest(headers = Accept(mediaRanges.toList) :: Nil))

  def marshalToResponse[T: ToResponseMarshaller](
      value: T,
      request: HttpRequest = HttpRequest(),
    ): RIO[Clock with RouteTestConfig, HttpResponse] =
    fromFutureWithMarshalingTimeout(implicit ec => Marshal(value).toResponseFor(request))

  def unmarshal[T: FromEntityUnmarshaller](entity: HttpEntity): RIO[Environment, T] =
    for {
      mat <- ZIO.access[Mat](_.get)
      res <- fromFutureWithMarshalingTimeout { implicit ec =>
               implicit val materializer: Materializer = mat
               Unmarshal(entity).to[T]
             }
    } yield res
}
