
lazy val all = project.in(file("."))
  .settings(moduleName := "happy-path-all")
  .aggregate(core, tests, docs)
  .settings(Common.settings:_*)
  .settings(Common.noPublishing: _*)

lazy val core = project.in(file("core"))
  .settings(moduleName := "happy-path")
  .settings(Common.settings:_*)
  .settings(Dependencies.settings:_*)
  .settings(Publish.settings:_*)
  .settings(Format.settings:_*)

lazy val tests = project.in(file("tests"))
  .dependsOn(core)
  .aggregate(core)
  .settings(moduleName := "happy-path-tests")
  .settings(Common.settings:_*)
  .settings(Common.noPublishing: _*)
  .settings(Dependencies.testSettings:_*)
  .settings(Format.settings:_*)
  .settings(Testing.settings:_*)

lazy val docs = project.in(file("docs"))
  .dependsOn(core)
  .settings(moduleName := "happy-path-docs")
  .settings(Dependencies.settings:_*)
  .settings(tutSettings)
  .settings(tutScalacOptions ~= (_.filterNot(Set("-Ywarn-unused-import", "-Ywarn-dead-code"))))
  .settings(tutTargetDirectory := file("."))
  .settings(Common.noPublishing: _*)



addCommandAlias("validate", ";project all;clean;compile;test;tut")
