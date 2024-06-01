val AIRFRAME_VERSION    = "24.5.2"
val AIRSPEC_VERSION     = "24.5.2"
val TRINO_VERSION       = "448"
val AWS_SDK_VERSION     = "2.20.146"
val SCALAJS_DOM_VERSION = "2.8.0"

val SCALA_3 = IO.read(file("SCALA_VERSION")).trim
ThisBuild / scalaVersion := SCALA_3

// For using the internal Maven repo at jfrog.io
val jfrogCredential = Credentials(
  "Artifactory Realm",
  "treasuredata.jfrog.io",
  sys.env.getOrElse("TD_ARTIFACTORY_USERNAME", ""),
  sys.env.getOrElse("TD_ARTIFACTORY_PASSWORD", "")
)

val TD_MAVEN_REPO          = "release" at "https://treasuredata.jfrog.io/treasuredata/libs-release"
val TD_MAVEN_SNAPSHOT_REPO = "snapshot" at "https://treasuredata.jfrog.io/treasuredata/libs-snapshot"
ThisBuild / credentials += jfrogCredential
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++ Seq(
  TD_MAVEN_REPO,
  TD_MAVEN_SNAPSHOT_REPO
)

Global / onChangedBuildSource := ReloadOnSourceChanges

val buildSettings = Seq[Setting[?]](
  organization       := "com.treasuredata.flow",
  description        := "Incremental Query Compiler and Scheduler",
  crossPaths         := true,
  publishMavenStyle  := true,
  Test / logBuffered := false,
  libraryDependencies ++= Seq(
    "org.wvlet.airframe" %%% "airspec" % AIRSPEC_VERSION % Test
  ),
  testFrameworks += new TestFramework("wvlet.airspec.Framework")
)

lazy val jvmProjects: Seq[ProjectReference] = Seq(
  api.jvm,
  server,
  lang,
  client.jvm
)

lazy val jsProjects: Seq[ProjectReference] = Seq(
  api.js,
  client.js,
  ui,
  uiMain
)

val noPublish = Seq(
  publishArtifact := false,
  publish         := {},
  publishLocal    := {},
  publish / skip  := true
)

Global / excludeLintKeys += ideSkipProject

lazy val projectJVM =
  project
    .settings(noPublish)
    .settings(
      // Skip importing aggregated projects in IntelliJ IDEA
      ideSkipProject := true
      // Use a stable coverage directory name without containing scala version
      // coverageDataDir := target.value
    )
    .aggregate(jvmProjects *)

lazy val projectJS =
  project
    .settings(noPublish)
    .settings(
      // Skip importing aggregated projects in IntelliJ IDEA
      ideSkipProject := true
    )
    .aggregate(jsProjects *)

lazy val api =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("flow-api"))
    .enablePlugins(AirframeHttpPlugin, BuildInfoPlugin)
    .settings(
      buildSettings,
      name := "flow-api",
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        scalaVersion,
        sbtVersion
      ),
      buildInfoOptions += BuildInfoOption.BuildTime,
      buildInfoPackage := "com.treasuredata.flow",
      libraryDependencies ++= Seq(
        "org.wvlet.airframe" %%% "airframe-http"    % AIRFRAME_VERSION,
        "org.wvlet.airframe" %%% "airframe-metrics" % AIRFRAME_VERSION
      )
    )

lazy val lang =
  project
    .enablePlugins(Antlr4Plugin)
    .in(file("flow-lang"))
    .settings(
      buildSettings,
      name                       := "flow-lang",
      Antlr4 / antlr4Version     := "4.13.1",
      Antlr4 / antlr4PackageName := Some("com.treasuredata.flow.lang.compiler.parser"),
      Antlr4 / antlr4GenListener := true,
      Antlr4 / antlr4GenVisitor  := true,
      Test / javaOptions ++= Seq(
        // "--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED",
        // Add JVM options for suppress warnings in TestTrinoServer
        "-Djdk.attach.allowAttachSelf=true",
        "-XX:+EnableDynamicAgentLoading"
      ),
      libraryDependencies ++= Seq(
        "org.wvlet.airframe" %% "airframe"          % AIRFRAME_VERSION,
        "org.wvlet.airframe" %% "airframe-launcher" % AIRFRAME_VERSION,
        "org.wvlet.airframe" %% "airframe-ulid"     % AIRFRAME_VERSION,
        // Add a reference implementation of the compiler
        "org.scala-lang" %% "scala3-compiler" % SCALA_3 % Test,
        // Add sql parser for testing purpose
        "org.wvlet.airframe" %% "airframe-sql" % AIRFRAME_VERSION % Test,
        "org.apache.arrow"    % "arrow-vector" % "16.1.0",
        "org.duckdb"          % "duckdb_jdbc"  % "0.10.3",
        "io.trino"            % "trino-jdbc"   % TRINO_VERSION,
        // exclude() and jar() are necessary to avoid https://github.com/sbt/sbt/issues/7407
        "io.trino" % "trino-testing" % TRINO_VERSION % Test exclude ("io.trino", "trino-tpch"),
        "io.trino" % "trino-memory"  % TRINO_VERSION % Test exclude ("io.trino", "trino-tpch") jar ()

//        // Add Spark as a reference impl (Scala 2)
//        "org.apache.spark" %% "spark-sql" % "3.5.1" % Test excludeAll (
//          // exclude sbt-parser-combinators as it conflicts with Scala 3
//          ExclusionRule(organization = "org.scala-lang.modules", name = "scala-parser-combinators_2.13")
//        ) cross (CrossVersion.for3Use2_13)
      ),
      // To enable JVM options
      // TODO: Fix AirSpec -L option doesn't work for forked JVM.
      // Test / fork := true,
      // When forking, the base directory should be set to the root directory
      Test / baseDirectory := (ThisBuild / baseDirectory).value,
      // Watch changes of example .flow files upon testing
      Test / watchSources ++= ((ThisBuild / baseDirectory).value / "examples" ** "*.flow").get
    )
    .dependsOn(api.jvm)

lazy val server =
  project
    .in(file("flow-server"))
    .settings(
      buildSettings,
      name := "flow-server",
      libraryDependencies ++= Seq(
        // For redirecting slf4j logs to airframe-log
        "org.slf4j"           % "slf4j-jdk14"         % "2.0.13",
        "org.wvlet.airframe" %% "airframe-http-netty" % AIRFRAME_VERSION
      ),
      reStart / baseDirectory := (ThisBuild / baseDirectory).value
    ).dependsOn(api.jvm)

lazy val client =
  crossProject(JVMPlatform, JSPlatform)
    .in(file("flow-client"))
    .enablePlugins(AirframeHttpPlugin)
    .settings(
      buildSettings,
      airframeHttpClients := Seq(
        "com.treasuredata.flow.api.v1.frontend:rpc:FrontendRPC"
      )
    ).dependsOn(api)

import org.scalajs.linker.interface.{StandardConfig, OutputPatterns}
import org.scalajs.linker.interface.{ModuleKind, ModuleSplitStyle}

lazy val ui =
  project
    .enablePlugins(ScalaJSPlugin)
    .in(file("flow-ui"))
    .settings(
      buildSettings,
      name                   := "flow-ui",
      description            := "UI components that can be testable with Node.js",
      Test / jsEnv           := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
      Test / requireJsDomEnv := true,
      libraryDependencies ++= Seq(
        "org.wvlet.airframe" %%% "airframe"         % AIRFRAME_VERSION,
        "org.wvlet.airframe" %%% "airframe-http"    % AIRFRAME_VERSION,
        "org.wvlet.airframe" %%% "airframe-rx-html" % AIRFRAME_VERSION,
        "org.scala-js"       %%% "scalajs-dom"      % SCALAJS_DOM_VERSION
      )
    )
    .dependsOn(api.js, client.js)

lazy val uiMain =
  project
    .enablePlugins(ScalaJSPlugin, ScalablyTypedConverterExternalNpmPlugin)
    .in(file("flow-ui-main"))
    .settings(
      buildSettings,
      name                            := "flow-ui-main",
      description                     := "UI main code compiled with Vite.js",
      Test / jsEnv                    := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= {
        linkerConfig(_)
      },
      externalNpm := {
        scala.sys.process.Process(List("npm", "install", "--silent", "--no-audit", "--no-fund"), baseDirectory.value).!
        baseDirectory.value
      }
    )
    .dependsOn(ui)

def linkerConfig(config: StandardConfig): StandardConfig =
  config
    // Check IR works properly since Scala.js 1.16.0 https://github.com/scala-js/scala-js/pull/4867
    .withCheckIR(true)
    .withSourceMap(true)
    .withModuleKind(ModuleKind.ESModule)
    .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("com.treasuredata.flow.ui")))
