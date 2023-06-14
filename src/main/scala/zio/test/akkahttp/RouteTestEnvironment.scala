package zio.test.akkahttp

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import com.typesafe.config.{Config, ConfigFactory}
import zio._

object RouteTestEnvironment {
  lazy val testSystemFromConfig: URLayer[Config, ActorSystem] = ZLayer.scoped {
    ZIO.acquireRelease(ZIO.serviceWith[Config](conf => ActorSystem(actorSystemNameFrom(getClass), conf))) { sys =>
      ZIO.fromFuture(_ => sys.terminate()).orDie
    }
  }

  private def actorSystemNameFrom(clazz: Class[_]) =
    clazz.getName
      .replace('.', '-')
      .replace('_', '-')
      .filter(_ != '$')

  def testConfigSource: String = ""

  lazy val testConfig: ULayer[Config] =
    ZLayer.succeed {
      val source = testConfigSource
      val config = if (source.isEmpty) ConfigFactory.empty() else ConfigFactory.parseString(source)
      config.withFallback(ConfigFactory.load())
    }

  lazy val testMaterializer: URLayer[ActorSystem, Materializer] =
    ZLayer.fromFunction(SystemMaterializer(_: ActorSystem).materializer)

  type TestEnvironment = ActorSystem with Materializer with RouteTest.Config

  lazy val environmentFromConfig: URLayer[Config, TestEnvironment] =
    ZLayer.makeSome[Config, TestEnvironment](
      testSystemFromConfig,
      testMaterializer,
      RouteTest.testConfig,
    )

  lazy val environment: ULayer[TestEnvironment] = ZLayer.make[TestEnvironment](
    testConfig,
    environmentFromConfig,
  )
}
