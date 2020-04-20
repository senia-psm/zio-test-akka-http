package zio.test.akkahttp

import zio.duration._
import zio.test.environment.{testEnvironment, TestEnvironment}
import zio.test.{RunnableSpec, TestAspect, TestExecutor, TestRunner}

trait DefaultAkkaRunnableSpec
    extends RunnableSpec[RouteTestEnvironment.TestEnvironment with TestEnvironment, Any]
    with RouteTest {
  override def aspects
      : List[TestAspect[Nothing, RouteTestEnvironment.TestEnvironment with TestEnvironment, Nothing, Any]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[RouteTestEnvironment.TestEnvironment with TestEnvironment, Any] =
    TestRunner(TestExecutor.default(RouteTestEnvironment.environment ++ testEnvironment))

}
