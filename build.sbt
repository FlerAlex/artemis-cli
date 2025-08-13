import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport._

ThisBuild / version := "0.2.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.3"

lazy val root = (project in file("."))
  .settings(
    name := "artemis-cli",
    libraryDependencies ++= Seq(
      // Artemis JMS Client
      "org.apache.activemq" % "artemis-jms-client" % "2.31.2",
      // This is the correct JMS API for the Artemis client.
      "javax.jms" % "javax.jms-api" % "2.0.1",

      // Logging libraries (SLF4J API + Logback backend)
      "org.slf4j" % "slf4j-api" % "2.0.13",
      "ch.qos.logback" % "logback-classic" % "1.5.6",

      // Cats Effect for functional programming with side effects
      "org.typelevel" %% "cats-effect" % "3.5.4",

      // Command-line argument parsing
      "com.monovore" %% "decline" % "2.4.1",
      "com.monovore" %% "decline-effect" % "2.4.1",

      // Functional Streams for Scala
      "co.fs2" %% "fs2-core" % "3.10.2",

      // Use the correct, modern PureConfig module for Cats Effect integration.
      "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.9"
    ),

    // === Assembly Configuration ===
    assembly / mainClass := Some("Main"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",

    // Define a merge strategy that discards dependency manifests.
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF")  => MergeStrategy.discard
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", xs @ _*)
          if xs.last.toLowerCase.endsWith(".sf") || xs.last.toLowerCase
            .endsWith(".dsa") || xs.last.toLowerCase.endsWith(".rsa") =>
        MergeStrategy.discard
      case "reference.conf" | "application.conf" => MergeStrategy.concat
      case "module-info.class"                   => MergeStrategy.discard
      case _                                     => MergeStrategy.first
    }
  )
