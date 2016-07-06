import sbt.Keys._

lazy val nexusPublishingSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  publishTo := {
    val nexus = "http://nexus.wdf.sap.corp:8081/nexus/content/repositories/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "deploy.snapshots.marmolata/") 
    else
      Some("releases"  at nexus + "deploy.milestones.marmolata/")
  },
  javacOptions in (Compile,doc) ++= Seq("-notimestamp", "-linksource")
)

// see also https://github.com/scalastyle/scalastyle-sbt-plugin/issues/47
lazy val fixScalastyle = Seq(
  (scalastyleSources in Compile) <++= unmanagedSourceDirectories in Compile
)

lazy val commonSettings = Seq(
  version := "0.1.90",
  organization := "com.sap.marmolata",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  scalaVersion := "2.11.8",
  scalaJSUseRhino in Global := false
) ++ nexusPublishingSettings ++ fixScalastyle

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

lazy val selfrx = (crossProject in file("implementations/selfrx")).
  jsSettings(jsSettings: _*).jvmSettings(jvmSettings: _*).
  settings(
    name := "reactive-impl-self"
  ).jsSettings(
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.0",
    libraryDependencies += "be.doeraene" %%% "scalajs-jquery" % "0.9.0"
  ).dependsOn(root % "compile->compile;test->test")

lazy val selfrxJS = selfrx.js
lazy val selfrxJVM = selfrx.jvm

lazy val usageSelfrx = (crossProject in file("usage/selfrx")).
  jsSettings(jsSettings: _*).jvmSettings(jvmSettings: _*).
  settings(
    name := "reactive-usage-selfrx"
  ).
  dependsOn(selfrx, root % "compile->compile;test->test")

lazy val usageSelfrxJVM = usageSelfrx.jvm
lazy val usageSelfrxJS = usageSelfrx.js

lazy val debugSelfrx = (crossProject in file("usage/debug-selfrx")).
  jsSettings(jsSettings: _*).jvmSettings(jvmSettings: _*).
  settings(
    name := "reactive-usage-selfrx-debug"
  ).
  dependsOn(selfrx, root % "compile->compile;test->test")

lazy val debugSelfrxJVM = debugSelfrx.jvm
lazy val debugSelfrxJS = debugSelfrx.js
