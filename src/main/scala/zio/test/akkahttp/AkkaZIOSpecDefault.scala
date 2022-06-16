package zio.test.akkahttp

import zio._
import zio.duration._
import zio.clock.Clock
import zio.test._
import zio.test.environment.{testEnvironment, TestEnvironment}

trait AkkaZIOSpecDefault
    extends RunnableSpec[RouteTestEnvironment.TestEnvironment with TestEnvironment, Any]
    with RouteTest {

  override def aspects: List[TestAspect[Nothing, TestEnvironment, Nothing, Any]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[RouteTestEnvironment.TestEnvironment with TestEnvironment, Any] =
    TestRunner(TestExecutor.default(testEnvironment >+> RouteTestEnvironment.environment))

  /** Returns an effect that executes a given spec, producing the results of the execution.
    */
  private[zio] override def runSpec(
      spec: ZSpec[Environment, Failure],
    ): URIO[TestLogger with Clock, ExecutedSpec[Failure]] =
    runner.run(aspects.foldLeft(spec)(_ @@ _) @@ TestAspect.fibers)

  /** Builds a suite containing a number of other specs.
    */
  def suite[R, E, T](label: String)(specs: Spec[R, E, T]*): Spec[R, E, T] = zio.test.suite(label)(specs: _*)

  /** Builds an effectual suite containing a number of other specs.
    */
  def suiteM[R, E, T](label: String)(specs: ZIO[R, E, Iterable[Spec[R, E, T]]]): Spec[R, E, T] =
    zio.test.suiteM(label)(specs)

  /** Builds a spec with a single pure test.
    */
  def test(label: String)(assertion: => TestResult)(implicit loc: SourceLocation): ZSpec[Any, Nothing] =
    zio.test.test(label)(assertion)

  /** Builds a spec with a single effectful test.
    */
  def testM[R, E](label: String)(assertion: => ZIO[R, E, TestResult])(implicit loc: SourceLocation): ZSpec[R, E] =
    zio.test.testM(label)(assertion)

}
