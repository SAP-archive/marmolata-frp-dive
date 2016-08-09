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
  version := "0.2.0",
  organization := "com.sap.marmolata",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  scalaVersion := "2.11.8",
  // TODO: this command is used to use node instead of RhinoJS, but unfortunately this
  //       makes ScalaTest crash with 'cannot read property filename$1 of undefined'
  //       this is bad since the tests are really _much_ slower like this
  //       see also https://github.com/scalatest/scalatest/issues/926
  scalaJSUseRhino in Global := false,
  logBuffered in Test := false
) ++ nexusPublishingSettings ++ fixScalastyle

lazy val jsSettings = commonSettings ++ Seq(
  libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
  libraryDependencies += "org.typelevel" %%% "cats" % "0.5.0",
  libraryDependencies += "org.typelevel" %%% "discipline" % "0.4"
)

lazy val jvmSettings = commonSettings ++ Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  libraryDependencies += "org.typelevel" %% "cats" % "0.5.0",
  libraryDependencies += "org.typelevel" %% "discipline" % "0.4",
  libraryDependencies += "org.pegdown" % "pegdown" % "1.6.0" % "test",
  testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-h", "target/test-reports")
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

lazy val usageInterface = (crossProject in file("usage/interface")).
  jsSettings(jsSettings: _*).jvmSettings(jvmSettings: _*).
  settings(
    name := "reactive-usage-implementation"
  )

lazy val usageInterfaceJS = usageInterface.js
lazy val usageInterfaceJVM = usageInterface.jvm

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

lazy val example =
  (project in file("example")).
    enablePlugins(ScalaJSPlugin).
    settings(commonSettings: _*).settings(name := "reactive-example").
    dependsOn(debugSelfrxJS).
  settings(
    jsDependencies += "org.webjars.bower" % "jquery" % "1.11.1" / "dist/jquery.js",
    jsDependencies += "org.webjars.npm" % "source-map" % "0.5.6" / "dist/source-map.js",
    jsDependencies += "org.webjars.bower" % "bootstrap" % "3.3.6" / "dist/js/bootstrap.js" dependsOn "dist/jquery.js",
    jsDependencies += "org.webjars.npm" % "vis" % "4.16.1" / "dist/vis.js",
    jsDependencies += ("org.webjars.bower" % "bootstrap-treeview" % "1.2.0" exclude("org.webjars.bower", "bootstrap") exclude ("org.webjars.bower", "jquery")) / "dist/bootstrap-treeview.min.js" dependsOn "dist/js/bootstrap.js"
  )
