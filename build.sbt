import com.typesafe.sbt.SbtGit.GitKeys._
import xerial.sbt.Sonatype._
import ReleaseTransformations._

val scala212 = "2.12.10"
val scala213 = "2.13.1"

val sharedSettings = Seq(
  organization := "com.github.eikek",
  scalaVersion := scala213,
  scalacOptions ++=
    Seq("-feature", "-deprecation", "-unchecked", "-encoding", "UTF-8", "-language:higherKinds") ++
      (if (scalaBinaryVersion.value.startsWith("2.12"))
         List(
           "-Xfatal-warnings", // fail when there are warnings
           "-Xlint",
           "-Yno-adapted-args",
           "-Ywarn-dead-code",
           "-Ywarn-unused-import",
           "-Ypartial-unification",
           "-Ywarn-value-discard"
         )
       else if (scalaBinaryVersion.value.startsWith("2.13"))
         List("-Werror", "-Wdead-code", "-Wunused", "-Wvalue-discard")
       else
         Nil),
  crossScalaVersions := Seq(scala212, scala213),
  scalacOptions in Test := Seq(),
  scalacOptions in (Compile, console) := Seq(),
  licenses := Seq("MIT" -> url("http://spdx.org/licenses/MIT")),
  homepage := Some(url("https://github.com/eikek/fs2-bcpg"))
) ++ publishSettings

lazy val publishSettings = Seq(
  publishTo := sonatypePublishToBundle.value,
  publishMavenStyle := true,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/eikek/fs2-bcpg.git"),
      "scm:git:git@github.com:eikek/fs2-bcpg.git"
    )
  ),
  developers := List(
    Developer(
      id = "eikek",
      name = "Eike Kettner",
      url = url("https://github.com/eikek"),
      email = ""
    )
  ),
  publishArtifact in Test := false,
  releaseCrossBuild := true,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    //For non cross-build projects, use releaseStepCommand("publishSigned")
    releaseStepCommandAndRemaining("+publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  ),
  sonatypeProjectHosting := Some(GitHubHosting("eikek", "fs2-bcpg", "eike.kettner@posteo.de"))
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

val testSettings = Seq(
  testFrameworks += new TestFramework("minitest.runner.Framework"),
  libraryDependencies ++=
    (Dependencies.minitest ++
      Dependencies.logback).map(_ % Test)
)

val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    sbtVersion,
    gitHeadCommit,
    gitHeadCommitDate,
    gitUncommittedChanges,
    gitDescribedVersion
  ),
  buildInfoOptions += BuildInfoOption.ToJson,
  buildInfoOptions += BuildInfoOption.BuildTime
)

lazy val pgp = project.in(file("modules/pgp")).
  enablePlugins(BuildInfoPlugin).
  settings(sharedSettings).
  settings(testSettings).
  settings(buildInfoSettings).
  settings(
    name := "fs2-bcpgp",
    description := "FS2 wrapper for bouncycastle's PGP",
    buildInfoPackage := "fs2bcpgp",
    libraryDependencies ++=
      Dependencies.fs2 ++
      Dependencies.bcpg ++
      Dependencies.scodecBits ++
      (Dependencies.logback ++ Dependencies.minitest).map(_ % Test)
    )

lazy val root = project.in(file(".")).
  settings(sharedSettings).
  settings(noPublish).
  settings(
    name := "fs2-bcpgp-root"
  ).
  aggregate(pgp)
