import BuildHelper._

inThisBuild(
  List(
    organization := "info.senia",
    homepage     := Some(uri("https://github.com/senia-psm/zio-test-akka-http")),
    licenses     := List(License.Apache2),
    developers   := List(
      Developer(
        "senia",
        "Simon Popugaev",
        "seniapsm@gmail.com",
        uri("https://github.com/afsaltha://github.com/senia-psm"),
      ),
    ),
    scmInfo := Some(
      ScmInfo(
        uri("https://github.com/senia-psm/zio-test-akka-http"),
        "scm:git:git@github.com:senia-psm/zio-test-akka-http.git",
      ),
    ),
    pomIncludeRepository := { _ => false },
    publishMavenStyle    := true,
    publishTo            := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if version.value.endsWith("-SNAPSHOT") then Some("central-snapshots" at centralSnapshots)
      else localStaging.value
    },
  ),
)

addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt")
addCommandAlias("check", ";scalafmtCheckAll;scalafmtSbtCheck")

lazy val zioVersion      = "2.1.27"
lazy val akkaVersion     = "2.6.20"
lazy val akkaHttpVersion = "10.2.10"

lazy val root =
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
