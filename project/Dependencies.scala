import sbt._

object Dependencies {
  type Version = String

  object Versions {
    val cats: Version = "2.6.1"
    val catsEffect: Version = "3.2.0"
    val decline: Version = "2.2.0"
    val fs2: Version = "3.2.1"
    val mouse: Version = "1.0.7"
    val doobie: Version = "1.0.0-M5"
    val pureconfig: Version = "0.17.0"
    val sttp: Version = "3.3.16"
    val jsoup: Version = "1.14.3"
    val logbackClassic: Version = "1.3.0-alpha4"
    val log4cats: Version = "2.1.1"
    val betterFiles: Version = "3.9.1"
    val scalaCSVParser: Version = "0.13.1"
    val scalaCSV: Version = "1.3.8"
    val akka: Version = "2.6.17"
    val akkaHttp: Version = "10.2.7"
    val circe: Version = "0.14.0" // "0.15.0-M1" (fs2 works only on 0.14.0)
    // val luceneSpatial: Version = "7.7.3"
  }

  lazy val akka: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor",
    "com.typesafe.akka" %% "akka-actor-typed",
    "com.typesafe.akka" %% "akka-stream",
  ).map(_ % Versions.akka) ++ Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed",
    "com.typesafe.akka" %% "akka-testkit",
    "com.typesafe.akka" %% "akka-stream-testkit"
  ).map(_ % Versions.akka % Test) ++ Seq(
    "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp
  )

  lazy val dependencies: Seq[ModuleID] = {
    Seq(
      "org.typelevel" %% "cats-core" % Versions.cats,

      "org.typelevel" %% "mouse" % Versions.mouse,
      "org.tpolecat" %% "doobie-core" % Versions.doobie,

      "com.github.pureconfig" %% "pureconfig" % Versions.pureconfig,

      "org.jsoup" % "jsoup" % Versions.jsoup,

      "ch.qos.logback" % "logback-classic" % Versions.logbackClassic,

      "com.github.pathikrit" %% "better-files" % Versions.betterFiles,
      "com.github.tototoshi" %% "scala-csv" % Versions.scalaCSV,

      "org.apache.lucene" % "lucene-spatial" % "8.4.1",
      "org.apache.lucene" % "lucene-spatial-extras" % "8.10.1"

    ) ++ Seq(
      "com.monovore" %% "decline",
      "com.monovore" %% "decline-effect",
    ).map(_ % Versions.decline) ++ Seq(
      "org.typelevel" %% "cats-effect" % Versions.catsEffect,
    ) ++ Seq(
      "co.fs2" %% "fs2-core",
      "co.fs2" %% "fs2-io",
      "co.fs2" %% "fs2-reactive-streams"
    ).map(_ % Versions.fs2) ++ Seq(
      "com.softwaremill.sttp.client3" %% "core",
      "com.softwaremill.sttp.client3" %% "circe",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats",
      "com.softwaremill.sttp.client3" %% "slf4j-backend"
    ).map(_ % Versions.sttp) ++ Seq(
      "org.typelevel" %% "log4cats-core",
      "org.typelevel" %% "log4cats-slf4j"
    ).map(_ % Versions.log4cats) ++ akka ++ Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-fs2"
    ).map(_ % Versions.circe)
  }

  lazy val repositories: Seq[MavenRepository] = Seq(
    "Maven Central Server" at "https://repo1.maven.org/maven2",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "Typesafe repository snapshots" at "https://repo.typesafe.com/typesafe/snapshots/",
    "Typesafe repository releases" at "https://repo.typesafe.com/typesafe/releases/",
    "Sonatype repo" at "https://oss.sonatype.org/content/groups/scala-tools/",
    "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases",
    "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Sonatype staging" at "https://oss.sonatype.org/content/repositories/staging",
    "Java.net Maven2 Repository" at "https://download.java.net/maven/2/",
    "Twitter Repository" at "https://maven.twttr.com"
  )
}
