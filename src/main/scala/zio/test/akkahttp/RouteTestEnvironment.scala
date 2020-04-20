package zio.test.akkahttp

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import zio._
import RouteTest.{Mat, RouteTestConfig, System}

object RouteTestEnvironment {
  lazy val testSystemFromConfig: URLayer[Config, System] =
    ZLayer
      .fromAcquireRelease(ZIO.access[Config](ActorSystem(actorSystemNameFrom(getClass), _))) { sys =>
        ZIO.fromFuture(_ => sys.terminate()).orDie
      }

  lazy val testSystem: ULayer[System] = testConfig >>> testSystemFromConfig

  private def actorSystemNameFrom(clazz: Class[_]) =
    clazz.getName
      .replace('.', '-')
      .replace('_', '-')
      .filter(_ != '$')

  def testConfigSource: String = ""

  lazy val testConfig: ULayer[Config] =
    ZLayer.succeedMany {
      val source = testConfigSource
      val config = if (source.isEmpty) ConfigFactory.empty() else ConfigFactory.parseString(source)
      config.withFallback(ConfigFactory.load())
    }

  lazy val testMaterializer: URLayer[System, Mat] = ZLayer.fromService(SystemMaterializer(_).materializer)

  type TestEnvironment = System with Mat with RouteTestConfig

  lazy val environment: ULayer[TestEnvironment] =
    testSystem >>> (ZLayer.requires[System] ++ testMaterializer) ++ RouteTest.testConfig
}
