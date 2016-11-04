import sbt.Keys._
import sbt._

object Dependencies {
  object Versions {
    val specs2 = "3.6.2"
  }

  val cats = Seq("org.typelevel" %% "cats" % "0.7.2")

  val test = Seq(
    "org.specs2" %% "specs2-core" % Versions.specs2 % "test",
    "org.specs2" %% "specs2-scalacheck" % Versions.specs2 % "test"
  )

  val commonSettings = Seq(
    scalaVersion in ThisBuild := "2.11.8",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.bintrayRepo("scalaz", "releases"),
      Resolver.jcenterRepo
    ),
    libraryDependencies ++= cats,

    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1"),
    addCompilerPlugin("com.milessabin" % "si2712fix-plugin" % "1.1.0" cross CrossVersion.full)
  )

  val testSettings = commonSettings ++ Seq(
    libraryDependencies ++= test
  )

  val settings = commonSettings

}
