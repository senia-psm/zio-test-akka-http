package zio.test.akkahttp

import zio._
import zio.internal.stacktracer.Tracer
import zio.test.{TestEnvironment, ZIOSpec, ZSpec}

trait AkkaZIOSpecDefault extends ZIOSpec[RouteTestEnvironment.TestEnvironment with TestEnvironment] with RouteTest {

  override val layer: ZLayer[ZIOAppArgs with Scope, Any, RouteTestEnvironment.TestEnvironment with TestEnvironment] = {
    implicit val trace: zio.ZTraceElement = Tracer.newTrace
    zio.ZEnv.live >>> TestEnvironment.live >+> RouteTestEnvironment.environment
  }

  def spec: ZSpec[RouteTestEnvironment.TestEnvironment with TestEnvironment with Scope, Any]
}
