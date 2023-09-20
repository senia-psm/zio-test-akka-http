import sbt._
import Keys._

import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbtbuildinfo._
import BuildInfoKeys._

object BuildHelper {

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
  )

  private val std2xOptions = Seq(
    "-Xfatal-warnings",
    "-language:higherKinds",
    "-language:existentials",
    "-language:implicitConversions",
    "-explaintypes",
    "-Yrangepos",
    "-Xsource:2.13",
    "-Xlint:_,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-deprecation:false",
  )

  val buildInfoSettings = Seq(
    buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "zio",
    buildInfoObject  := "BuildInfo",
  )

  private val optimizerOptions = Seq("-opt:l:inline", "-opt-inline-from:zio.internal.**")

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 13)) =>
        std2xOptions ++ optimizerOptions
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:zio.internal.**",
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
        ) ++ std2xOptions ++ optimizerOptions
      case Some((2, 11)) =>
        Seq(
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Xexperimental",
          "-Ywarn-unused-import",
        ) ++ std2xOptions
      case _ => Seq.empty
    }

  val stdSettings = Seq(
    scalacOptions            := stdOptions,
    crossScalaVersions       := Seq("2.13.11", "2.12.18", "3.3.0"),
    ThisBuild / scalaVersion := crossScalaVersions.value.head,
    scalacOptions            := stdOptions ++ extraOptions(scalaVersion.value),
    Test / parallelExecution := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library"),
  )
}
