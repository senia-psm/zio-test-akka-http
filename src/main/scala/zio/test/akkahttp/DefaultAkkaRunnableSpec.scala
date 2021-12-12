package zio.test.akkahttp

import akka.actor.ActorSystem
import akka.stream.Materializer
import zio._
import zio.test.akkahttp.RouteTest.Config
import zio.test.{
  testEnvironment, Annotations, ExecutedSpec, Live, RunnableSpec, Sized, Spec, SuiteConstructor, TestAspect, TestClock,
  TestConfig, TestConsole, TestConstructor, TestEnvironment, TestExecutor, TestLogger, TestRandom, TestRunner,
  TestSystem, ZSpec,
}

trait DefaultAkkaRunnableSpec
    extends RunnableSpec[RouteTestEnvironment.TestEnvironment with TestEnvironment, Any]
    with RouteTest {
  override def aspects: List[TestAspect.WithOut[
    Nothing,
    RouteTestEnvironment.TestEnvironment with TestEnvironment,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
  ]] = List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[RouteTestEnvironment.TestEnvironment with TestEnvironment, Any] = {
    // TODO: remove workaround once izumi macros are ported to Scala 3.x
    val akkaEnv: ULayer[ActorSystem with Materializer with Config] = RouteTestEnvironment.environment
    val zEnv: ULayer[
      Annotations with Live with Sized with TestClock with TestConfig with TestConsole with TestRandom with TestSystem with Clock with Console with System with Random,
    ] = testEnvironment
    TestRunner(TestExecutor.default(akkaEnv ++ zEnv))
  }

  /** Returns an effect that executes a given spec, producing the results of the execution.
    */
  private[zio] override def runSpec(
      spec: ZSpec[Environment, Failure],
    )(implicit trace: ZTraceElement,
    ): URIO[TestLogger with Clock, ExecutedSpec[Failure]] =
    runner.run(aspects.foldLeft(spec)(_ @@ _) @@ TestAspect.fibers)

  /** Builds a suite containing a number of other specs.
    */
  def suite[In](
      label: String,
    )(specs: In*,
    )(implicit
      suiteConstructor: SuiteConstructor[In],
      trace: ZTraceElement,
    ): Spec[suiteConstructor.OutEnvironment, suiteConstructor.OutError, suiteConstructor.OutSuccess] =
    zio.test.suite(label)(specs: _*)

  /** Builds an effectual suite containing a number of other specs.
    */
  @deprecated("use suite", "2.0.0")
  def suiteM[R, E, T](
      label: String,
    )(specs: ZIO[R, E, Iterable[Spec[R, E, T]]],
    )(implicit
      trace: ZTraceElement,
    ): Spec[R, E, T] = suite(label)(specs)

  /** Builds a spec with a single test.
    */
  def test[In](
      label: String,
    )(assertion: => In,
    )(implicit
      testConstructor: TestConstructor[Nothing, In],
      trace: ZTraceElement,
    ): testConstructor.Out = zio.test.test(label)(assertion)

}
