/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package zio.test.akkahttp

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, MediaRange}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import akka.stream.Materializer
import zio._
import zio.clock.Clock
import zio.duration._
import zio.test.akkahttp.RouteTest.Environment

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait MarshallingTestUtils {

  private def fromFutureWithMarshalingTimeout[A](
      eff: ExecutionContext => Future[A],
      factor: Double = 1,
    ): ZIO[Has[RouteTest.Config] with Clock, Throwable, A] =
    for {
      config <- ZIO.service[RouteTest.Config]
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
      mat    <- ZIO.service[Materializer]
      config <- ZIO.service[RouteTest.Config]
      marshallingTimeout = config.marshallingTimeout
      res <- fromFutureWithMarshalingTimeout(
               implicit ec =>
                 Marshal(value)
                   .to[HttpEntity]
                   .flatMap(_.toStrict(FiniteDuration(marshallingTimeout.toNanos, TimeUnit.NANOSECONDS))(mat)),
               2,
             )
    } yield res

  def marshalToResponseForRequestAccepting[T: ToResponseMarshaller](
      value: T,
      mediaRanges: MediaRange*,
    ): RIO[Has[RouteTest.Config] with Clock, HttpResponse] =
    marshalToResponse(value, HttpRequest(headers = Accept(mediaRanges.toList) :: Nil))

  def marshalToResponse[T: ToResponseMarshaller](
      value: T,
      request: HttpRequest = HttpRequest(),
    ): RIO[Has[RouteTest.Config] with Clock, HttpResponse] =
    fromFutureWithMarshalingTimeout(implicit ec => Marshal(value).toResponseFor(request))

  def unmarshal[T: FromEntityUnmarshaller](entity: HttpEntity): RIO[Environment, T] =
    for {
      mat <- ZIO.service[Materializer]
      res <- fromFutureWithMarshalingTimeout { implicit ec =>
               implicit val materializer: Materializer = mat
               Unmarshal(entity).to[T]
             }
    } yield res
}
