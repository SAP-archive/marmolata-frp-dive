import sbt.Keys._

lazy val nexusPublishingSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  publishTo := Some("SAP Nexus" at "http://nexus.wdf.sap.corp:8081/nexus/content/repositories/deploy.milestones.marmolata/"),
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
)

lazy val commonSettings = Seq(
  version := "0.1.29",
  organization := "com.sap.marmolata",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  scalaVersion := "2.11.8") ++ nexusPublishingSettings

lazy val jsSettings = commonSettings ++ Seq(
  libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.0-M15" % "test",
  libraryDependencies += "org.typelevel" %%% "cats" % "0.5.0",
  libraryDependencies += "org.typelevel" %%% "discipline" % "0.4"
)

lazy val jvmSettings = commonSettings ++ Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0-M15" % "test",
  libraryDependencies += "org.typelevel" %% "cats" % "0.5.0",
  libraryDependencies += "org.typelevel" %% "discipline" % "0.4"
)

lazy val root = (crossProject in file("core")).
  jsSettings(jsSettings: _*).jvmSettings(jvmSettings: _*).
  settings(
    name := "reactive-interface"
  )

lazy val rootJS = root.js
lazy val rootJVM = root.jvm

lazy val scalarx = (crossProject in file("implementations/scalarx")).
  jsSettings(jsSettings: _*).jvmSettings(jvmSettings: _*).
  settings(
    name := "reactive-impl-scalarx",
    libraryDependencies += "com.lihaoyi" %%% "scalarx" % "0.3.1",
    libraryDependencies += "com.lihaoyi" %% "scalarx" % "0.3.1").
  dependsOn(root % "compile->compile;test->test")


lazy val scalarxJS = scalarx.js
lazy val scalarxJVM = scalarx.jvm

lazy val metarx = (crossProject in file("implementations/metarx")).
  jsSettings(jsSettings: _*).jvmSettings(jvmSettings: _*).
  settings(
    name := "reactive-impl-metarx",
    libraryDependencies += "pl.metastack" %%% "metarx" % "0.1.6",
    libraryDependencies += "pl.metastack" %% "metarx" % "0.1.6").
  dependsOn(root % "compile->compile;test->test")

lazy val metarxJS = metarx.js
lazy val metarxJVM = metarx.jvm

lazy val monix = (crossProject in file("implementations/monix")).
  jsSettings(commonSettings: _*).jvmSettings(commonSettings: _*).
  settings(
    name := "reactive-impl-monix",
    libraryDependencies += "io.monix" %%% "monix" % "2.0-RC2",
    libraryDependencies += "io.monix" %% "monix" % "2.0-RC2").
  dependsOn(root % "compile->compile;test->test")

lazy val monixJS = monix.js
lazy val monixJVM = monix.jvm

//libraryDependencies += "com.thoughtworks.binding" %%% "dom" % "latest.release"
