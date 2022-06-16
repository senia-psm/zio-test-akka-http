package akka.http.expose

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.`Sec-WebSocket-Protocol`
import akka.http.scaladsl.server._
import akka.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import akka.stream.Materializer
import zio.test.akkahttp.{RouteTest, RouteTestResult}
import zio._

import scala.concurrent.ExecutionContextExecutor

trait ExposedRouteTest {
  this: RouteTest =>

  protected def executeRequest(
      request: HttpRequest,
      route: Route,
    ): URIO[RouteTest.Environment with Has[ActorSystem], RouteTestResult.Lazy] =
    for {
      system <- ZIO.service[ActorSystem]
      config <- ZIO.service[RouteTest.Config]
      mat    <- ZIO.service[Materializer]
      res <- {
        implicit val actorSystem: ActorSystem                   = system
        implicit val executionContext: ExecutionContextExecutor = system.classicSystem.dispatcher
        implicit val materializer: Materializer                 = mat
        val routingSettings                                     = RoutingSettings(system)
        val parserSettings                                      = ParserSettings(system)
        val routingLog                                          = RoutingLog(system.classicSystem.log)

        val effectiveRequest =
          request.withEffectiveUri(
            securedConnection = config.defaultHost.securedConnection,
            defaultHostHeader = config.defaultHost.host,
          )
        val ctx = new RequestContextImpl(
          effectiveRequest,
          routingLog.requestLog(effectiveRequest),
          routingSettings,
          parserSettings,
        )

        val sealedExceptionHandler = ExceptionHandler.default(implicitly[RoutingSettings])

        val semiSealedRoute = // sealed for exceptions but not for rejections
          Directives.handleExceptions(sealedExceptionHandler)(route)

        ZIO
          .fromFuture(_ => semiSealedRoute(ctx))
          .orDie
          .flatMap {
            case RouteResult.Complete(response)   => RouteTestResult.LazyCompleted.make(response)
            case RouteResult.Rejected(rejections) => ZIO.succeed(RouteTestResult.Rejected(rejections))
          }
          .timeout(config.routeTestTimeout)
          .map(_.getOrElse(RouteTestResult.Timeout))
      }
    } yield res
}

object ExposedRouteTest {

  /** Check that the received response is a WebSocket upgrade response and extracts the chosen subprotocol.
    */
  def webSocketUpgradeWithProtocol(result: RouteTestResult.Completed): Option[String] =
    if (!result.isWebSocketUpgrade) None
    else
      result.header[`Sec-WebSocket-Protocol`].flatMap { h =>
        if (h.protocols.lengthCompare(1) == 0) h.protocols.headOption
        else None
      }
}
