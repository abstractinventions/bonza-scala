organization := "com.abstractinventions"

name := "bonza"

version := "0.2.0-SNAPSHOT"

scalaVersion := "2.10.2"

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

libraryDependencies ++= Seq(
    "net.databinder" %% "unfiltered-netty-server" % "0.6.8",
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
//    "com.github.theon" %% "scala-uri" % "0.3.6-SNAPSHOT",
    "commons-io" % "commons-io" % "2.4",
    "org.clapper" %% "avsl" % "1.0.1",
    "net.databinder" %% "unfiltered-spec" % "0.6.8" % "test"
)

resolvers ++= Seq(
    "jboss repo" at "http://repository.jboss.org/nexus/content/groups/public-jboss/",
    "Sonatype OSS" at "http://oss.sonatype.org/content/public"
)
