package zio.test.akkahttp

import akka.actor.ActorSystem
import akka.http.expose.ExposedRouteTest
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.stream.Materializer
import zio._
import zio.clock.Clock
import zio.duration._
import zio.test.akkahttp.RouteTest.Environment

import scala.concurrent.Future

trait RouteTest extends ExposedRouteTest with MarshallingTestUtils with RequestBuilding {

  implicit class WithTransformation(request: HttpRequest) {
    def ~>(f: HttpRequest => HttpRequest): HttpRequest = f(request)
    def ~>(header: HttpHeader): HttpRequest            = ~>(addHeader(header))
  }

  implicit class WithTransformationM[R, E](request: ZIO[R, E, HttpRequest]) {
    def ~>(f: HttpRequest => HttpRequest): ZIO[R, E, HttpRequest] = request.map(f)
    def ~>(header: HttpHeader): ZIO[R, E, HttpRequest]            = ~>(addHeader(header))
  }

  implicit class WithRoute(request: HttpRequest) {
    def ?~>(
        route: RequestContext => Future[RouteResult],
      ): URIO[Environment with Has[ActorSystem], RouteTestResult.Lazy] = executeRequest(request, route)

    def ~>(
        route: RequestContext => Future[RouteResult],
      ): URIO[Environment with Has[ActorSystem], RouteTestResult.Eager] =
      executeRequest(request, route).flatMap(
        _.toEager.catchAll[Any, Nothing, RouteTestResult.Eager](_ => ZIO.succeed(RouteTestResult.Timeout)),
      )
  }

  implicit class WithRouteM[R, E](request: ZIO[R, E, HttpRequest]) {
    def ?~>(
        route: RequestContext => Future[RouteResult],
      ): ZIO[Environment with Has[ActorSystem] with R, E, RouteTestResult.Lazy] =
      request.flatMap(executeRequest(_, route))

    def ~>(
        route: RequestContext => Future[RouteResult],
      ): ZIO[Environment with Has[ActorSystem] with R, E, RouteTestResult.Eager] =
      request
        .flatMap(executeRequest(_, route))
        .flatMap(_.toEager.catchAll[Any, Nothing, RouteTestResult.Eager](_ => ZIO.succeed(RouteTestResult.Timeout)))
  }
}

object RouteTest {
  type Environment = Has[Materializer] with Has[RouteTest.Config] with Clock

  case class DefaultHostInfo(host: Host, securedConnection: Boolean)

  case class Config(
      timeout: Duration = 15.seconds,
      marshallingTimeout: Duration = Duration.Finite(1.second.toNanos),
      routeTestTimeout: Duration = 1.second,
      defaultHost: DefaultHostInfo = DefaultHostInfo(Host("example.com"), securedConnection = false))

  val testConfig: ULayer[Has[RouteTest.Config]] = ZLayer.succeed(Config())
}
