name := "balance-transfer"

version := "1.0.0"

organization := "vit.home"

scalaVersion := "2.11.11"

resolvers ++= Seq(
  "snapshots"           at "http://oss.sonatype.org/content/repositories/snapshots",
  "releases"            at "http://oss.sonatype.org/content/repositories/releases",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "spray repo"          at "http://repo.spray.io"
)

Revolver.settings

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")

scalacOptions in Test ++= Seq("-Yrangepos")

fork := true
fork in Test := true
parallelExecution in Test := false

lazy val root = (project in file(".")).
  configs(IntegrationSpec).
  settings(inConfig(IntegrationSpec)(Defaults.testTasks): _*)

mainClass in(Compile, run) := Some("io.example.Boot")

lazy val IntegrationSpec = config("it") extend Test

def itFilter(name: String): Boolean = name endsWith "IntegrationSpec"
def unitFilter(name: String): Boolean = (name endsWith "Spec") && !itFilter(name)

testOptions in Test := Seq(Tests.Filter(unitFilter))
testOptions in IntegrationSpec := Seq(Tests.Filter(itFilter))

libraryDependencies ++= {
  val akkaHttpV = "10.0.9"
  val akkaV = "2.4.20"
  val specs2V = "3.9.4"

  Seq(
    "com.typesafe.akka"          %%  "akka-actor"               % akkaV,
    "com.typesafe.akka"          %%  "akka-slf4j"               % akkaV,
    "com.typesafe.akka"          %%  "akka-stream"              % akkaV,
    "com.typesafe.akka"          %%  "akka-http"                % akkaHttpV,
    "com.typesafe.akka"          %%  "akka-http-spray-json"     % akkaHttpV,

    "io.spray"                   %% "spray-caching"             % "1.3.4",
    "com.iheart"                 %% "ficus"                     % "1.4.1",
    "com.typesafe.scala-logging" %% "scala-logging"             % "3.5.0",
    "org.scalaj"                 %% "scalaj-http"               % "2.3.0",
    "com.typesafe"               %  "config"                    % "1.3.1",
    "ch.qos.logback"             %  "logback-classic"           % "1.2.3",
    "org.apache.commons"         %  "commons-lang3"             % "3.6",
    "joda-time"                  %  "joda-time"                 % "2.9.9",
    "org.joda"                   %  "joda-convert"              % "1.8.2",
    "commons-net"                %  "commons-net"               % "3.6",
    "commons-codec"              %  "commons-codec"             % "1.10",
    // test
    "com.typesafe.akka"   %%  "akka-http-testkit"      % akkaHttpV  % "test",
    "com.typesafe.akka"   %%  "akka-testkit"           % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"            % specs2V % "test",
    "org.specs2"          %%  "specs2-junit"           % specs2V % "test",
    "junit"               %   "junit"                  % "4.12"  % "test"
  )
}
