package zio.test.akkahttp

import akka.actor.ActorSystem
import akka.stream.Materializer
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio._
import zio.duration._
import zio.random.Random
import zio.system.System
import zio.test.akkahttp.RouteTest.Config
import zio.test.environment.{testEnvironment, Live, TestClock, TestConsole, TestEnvironment, TestRandom, TestSystem}
import zio.test.{Annotations, RunnableSpec, Sized, TestAspect, TestConfig, TestExecutor, TestRunner}

trait DefaultAkkaRunnableSpec
    extends RunnableSpec[RouteTestEnvironment.TestEnvironment with TestEnvironment, Any]
    with RouteTest {
  override def aspects
      : List[TestAspect[Nothing, RouteTestEnvironment.TestEnvironment with TestEnvironment, Nothing, Any]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[RouteTestEnvironment.TestEnvironment with TestEnvironment, Any] = {
    // TODO: remove workaround once izumi macros are ported to Scala 3.x
    val akkaEnv: ULayer[Has[ActorSystem] with Has[Materializer] with Has[Config]] = RouteTestEnvironment.environment
    val zEnv: ULayer[
      Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[TestClock.Service] with Has[
        TestConfig.Service,
      ] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[TestSystem.Service] with Has[
        Clock.Service,
      ] with Has[Console.Service] with Has[System.Service] with Has[Random.Service] with Has[Blocking.Service],
    ] = testEnvironment
    TestRunner(TestExecutor.default(akkaEnv ++ zEnv))
  }

}
