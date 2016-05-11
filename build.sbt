enablePlugins(ScalaJSPlugin)

name := "rx-tests"

version := "0.1"

scalaVersion := "2.11.8"

libraryDependencies += "com.thoughtworks.binding" %%% "dom" % "latest.release"
libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.0-M15" % "test"


addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

// the different libraries
libraryDependencies += "pl.metastack" %%% "metarx" % "0.1.6"  // Scala.js

libraryDependencies += "com.lihaoyi" %%% "scalarx" % "0.3.1"
