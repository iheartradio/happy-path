import sbt.Keys._
import sbt._
import sbtrelease.Version

object Common {
  val noPublishing = Seq(publish := (), publishLocal := (), publishArtifact := false)

  val settings = Seq(
    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
//      "-Xlog-implicits",
      "-Xlint"
    )
  )
}
