import sbt.Keys._

lazy val commonSettings = Seq(
  version := "0.1.10",
  organization := "com.sap.marmolata",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  scalaVersion := "2.11.8",
  libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.0-M15" % "test",
  libraryDependencies += "org.typelevel" %%% "cats" % "0.5.0",
  libraryDependencies += "org.typelevel" %%% "discipline" % "0.4"
)

lazy val root = (project in file("core")).
  enablePlugins(ScalaJSPlugin).
  settings(commonSettings: _*).
  settings(
    name := "reactive-interface"
  )

lazy val scalarx = (project in file("implementations/scalarx")).
  enablePlugins(ScalaJSPlugin).
  settings(commonSettings: _*).
  settings(
    name := "reactive-impl-scalarx",
    libraryDependencies += "com.lihaoyi" %%% "scalarx" % "0.3.1").
  dependsOn(root % "compile->compile;test->test")


lazy val metarx = (project in file("implementations/metarx")).
  enablePlugins(ScalaJSPlugin).
  settings(commonSettings: _*).
  settings(
    name := "reactive-impl-metarx",
    libraryDependencies += "pl.metastack" %%% "metarx" % "0.1.6").
  dependsOn(root % "compile->compile;test->test")

lazy val monix = (project in file("implementations/monix")).
  enablePlugins(ScalaJSPlugin).
  settings(commonSettings: _*).
  settings(
    name := "reactive-impl-monix",
    libraryDependencies += "io.monix" %%% "monix" % "2.0-RC2").
  dependsOn(root % "compile->compile;test->test")


//libraryDependencies += "com.thoughtworks.binding" %%% "dom" % "latest.release"
