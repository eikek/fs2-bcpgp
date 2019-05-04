import libs._
import Path.relativeTo
import com.typesafe.sbt.SbtGit.GitKeys._

lazy val sharedSettings = Seq(
  organization := "com.github.eikek",
  licenses := Seq("MIT" -> url("http://spdx.org/licenses/MIT")),
  homepage := Some(url("https://github.com/eikek/fs2-bcpgp")),
  scalaVersion := `scala-version`,
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-Xfatal-warnings",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-language:higherKinds",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused-import"
  ),
  scalacOptions in (Compile, console) ~= (_ filterNot (Set("-Xfatal-warnings", "-Ywarn-unused-import").contains)),
  scalacOptions in (Test) := (scalacOptions in (Compile, console)).value,
  testFrameworks += new TestFramework("minitest.runner.Framework")
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/eikek/fs2-bcpgp.git"),
      "scm:git:git@github.com:eikek/fs2-bcpgp.git"
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
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value
)

lazy val coreDeps = Seq(`cats-core`, `cats-effect`, `fs2-core`, `fs2-io`)
lazy val testDeps = Seq(minitest, `minitest-laws`, `logback-classic`).map(_ % "test")

lazy val pgp = project.in(file("modules/pgp")).
  enablePlugins(BuildInfoPlugin).
  settings(sharedSettings).
  settings(publishSettings).
  settings(
    name := "fs2-bcpgp",
    description := "FS2 wrapper for bouncycastle's PGP",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, gitHeadCommit, gitHeadCommitDate, gitUncommittedChanges, gitDescribedVersion),
    buildInfoPackage := "fs2bcpgp",
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoOptions += BuildInfoOption.BuildTime,
    libraryDependencies ++= coreDeps ++ testDeps ++ Seq(
      bcpg, `scodec-bits`
    ))

lazy val root = project.in(file(".")).
  disablePlugins(ReleasePlugin).
  settings(sharedSettings).
  aggregate(pgp)
