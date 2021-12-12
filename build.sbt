import BuildHelper._

inThisBuild(
  List(
    organization := "info.senia",
    homepage     := Some(url("https://github.com/senia-psm/zio-test-akka-http")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "senia",
        "Simon Popugaev",
        "seniapsm@gmail.com",
        url("https://github.com/afsaltha://github.com/senia-psm"),
      ),
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/senia-psm/zio-test-akka-http"),
        "scm:git:git@github.com:senia-psm/zio-test-akka-http.git",
      ),
    ),
  ),
)

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val zioVersion      = "2.0.0-M6-2"
lazy val akkaVersion     = "2.6.17"
lazy val akkaHttpVersion = "10.2.7"

lazy val zioTestAkkaHttp =
  Project("zio-test-akka-http", file("."))
    .settings(stdSettings)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio"          % zioVersion,
        "dev.zio" %% "zio-test"     % zioVersion,
        "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      ),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-http"    % akkaHttpVersion,
        "com.typesafe.akka" %% "akka-stream"  % akkaVersion,
        "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      ).map(_.cross(CrossVersion.for3Use2_13)),
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    )
