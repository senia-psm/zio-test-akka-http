package zio.test.akkahttp

import zio._
import zio.test.{Spec, TestEnvironment, ZIOSpec}

trait AkkaZIOSpecDefault extends ZIOSpec[RouteTestEnvironment.TestEnvironment with TestEnvironment] with RouteTest {

  override val bootstrap
      : ZLayer[ZIOAppArgs with Scope, Any, RouteTestEnvironment.TestEnvironment with TestEnvironment] =
    zio.ZEnv.live >>> TestEnvironment.live >+> RouteTestEnvironment.environment

  def spec: Spec[RouteTestEnvironment.TestEnvironment with TestEnvironment with Scope, Any]
}
