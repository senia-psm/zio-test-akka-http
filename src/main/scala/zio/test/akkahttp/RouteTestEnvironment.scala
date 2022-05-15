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

  lazy val testSystem: ULayer[ActorSystem] = testConfig >>> testSystemFromConfig

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
    ZLayer.fromFunction(SystemMaterializer(_).materializer)

  type TestEnvironment = ActorSystem with Materializer with RouteTest.Config

  lazy val environment: ULayer[TestEnvironment] =
    testSystem >>> ZLayer.environment[ActorSystem] ++ testMaterializer ++ RouteTest.testConfig
}
