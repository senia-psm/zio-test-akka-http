package zio.test.akkahttp

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import com.typesafe.config.{Config, ConfigFactory}
import zio._

object RouteTestEnvironment {
  lazy val testSystemFromConfig: URLayer[Has[Config], Has[ActorSystem]] =
    ZLayer.fromAcquireRelease(ZIO.service[Config].map(conf => ActorSystem(actorSystemNameFrom(getClass), conf)))(sys =>
      ZIO.fromFuture(_ => sys.terminate()).orDie,
    )

  lazy val testSystem: ULayer[Has[ActorSystem]] = testConfig >>> testSystemFromConfig

  private def actorSystemNameFrom(clazz: Class[_]) =
    clazz.getName
      .replace('.', '-')
      .replace('_', '-')
      .filter(_ != '$')

  def testConfigSource: String = ""

  lazy val testConfig: ULayer[Has[Config]] =
    ZLayer.succeed {
      val source = testConfigSource
      val config = if (source.isEmpty) ConfigFactory.empty() else ConfigFactory.parseString(source)
      config.withFallback(ConfigFactory.load())
    }

  lazy val testMaterializer: URLayer[Has[ActorSystem], Has[Materializer]] =
    ZLayer.fromService(SystemMaterializer(_: ActorSystem).materializer)

  type TestEnvironment = Has[ActorSystem] with Has[Materializer] with Has[RouteTest.Config]

  lazy val environment: ULayer[TestEnvironment] =
    testSystem >>> (ZLayer.requires[Has[ActorSystem]] ++ testMaterializer) ++ RouteTest.testConfig
}
