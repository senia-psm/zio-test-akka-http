package akka.http.expose

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.`Sec-WebSocket-Protocol`
import akka.http.scaladsl.server._
import akka.http.scaladsl.settings.RoutingSettings
import akka.stream.Materializer
import zio.test.Assertion
import zio.test.Assertion._
import zio.test.akkasupport.http.RouteTest.{Mat, RouteTestConfig, System}
import zio.test.akkasupport.http.{RouteTest, TestRouteResult}
import zio.{URIO, ZIO}

import scala.concurrent.ExecutionContextExecutor

trait ExposedRouteTest {
  this: RouteTest =>

  /**
    * Asserts that the received response is a WebSocket upgrade response and the extracts
    * the chosen subprotocol and passes it to the handler.
    */
  def expectWebSocketUpgradeWithProtocol(assertion: Assertion[String]): Assertion[TestRouteResult.Completed] =
    (isWebSocketUpgrade && header[`Sec-WebSocket-Protocol`](
      isSome(hasField("protocols", _.protocols, hasSize[String](equalTo(1)) && hasFirst(assertion)))
    )) ?? "expectWebSocketUpgradeWithProtocol"

  protected def executeRequest(request: HttpRequest, route: Route): URIO[TestRouteResult.Environment, TestRouteResult] =
    for {
      system <- ZIO.access[System](_.get)
      config <- ZIO.access[RouteTestConfig](_.get)
      mat    <- ZIO.access[Mat](_.get)
      res <- {
        implicit val actorSystem: ActorSystem                   = system
        implicit val executionContext: ExecutionContextExecutor = system.classicSystem.dispatcher
        implicit val materializer: Materializer                 = mat
        val routingSettings                                     = RoutingSettings(system)
        val routingLog                                          = RoutingLog(system.classicSystem.log)

        val effectiveRequest =
          request.withEffectiveUri(
            securedConnection = config.defaultHost.securedConnection,
            defaultHostHeader = config.defaultHost.host
          )
        val ctx = new RequestContextImpl(effectiveRequest, routingLog.requestLog(effectiveRequest), routingSettings)

        val sealedExceptionHandler = ExceptionHandler.default(implicitly[RoutingSettings])

        val semiSealedRoute = // sealed for exceptions but not for rejections
          Directives.handleExceptions(sealedExceptionHandler)(route)

        ZIO
          .fromFuture(_ => semiSealedRoute(ctx))
          .orDie
          .flatMap {
            case RouteResult.Complete(response)   => TestRouteResult.Completed.make(response)
            case RouteResult.Rejected(rejections) => ZIO.succeed(TestRouteResult.Rejected(rejections))
          }
          .timeout(config.routeTestTimeout)
          .map(_.getOrElse(TestRouteResult.Timeout))
      }
    } yield res
}
