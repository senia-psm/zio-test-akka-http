package zio.test.akkahttp

import zio._
import zio.test.{testEnvironment, Spec, TestEnvironment, ZIOSpec}

trait AkkaZIOSpecDefault extends ZIOSpec[RouteTestEnvironment.TestEnvironment with TestEnvironment] with RouteTest {

  override val bootstrap: ZLayer[Any, Any, RouteTestEnvironment.TestEnvironment with TestEnvironment] =
    testEnvironment >+> RouteTestEnvironment.environment

  def spec: Spec[RouteTestEnvironment.TestEnvironment with TestEnvironment with Scope, Any]
}
