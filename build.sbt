import BuildHelper._

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val zioVersion      = "1.0.0-RC18-2"
lazy val akkaVersion     = "2.6.4"
lazy val akkaHttpVersion = "10.1.11"

lazy val zioTestAkkaHttp =
  Project("zio-test-akka-http", file("."))
    .settings(stdSettings)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio"           %% "zio"          % zioVersion,
        "dev.zio"           %% "zio-test"     % zioVersion,
        "dev.zio"           %% "zio-test-sbt" % zioVersion % Test,
        "com.typesafe.akka" %% "akka-http"    % akkaHttpVersion,
        "com.typesafe.akka" %% "akka-stream"  % akkaVersion,
        "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
      ),
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
    )
