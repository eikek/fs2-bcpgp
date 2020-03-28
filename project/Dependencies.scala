import sbt._

object Dependencies {

  val fs2Version = "2.3.0"
  val minitestVersion = "2.7.0"
  val scodecVersion = "1.1.14"
  val bcpgVersion = "1.64"
  val logbackVersion = "1.2.3"

  // https://github.com/functional-streams-for-scala/fs2
  // MIT
  val fs2 = Seq(
    "co.fs2" %% "fs2-core" % fs2Version,
    "co.fs2" %% "fs2-io" % fs2Version
  )

  // https://github.com/monix/minitest
  // Apache 2.0
  val minitest = Seq(
    "io.monix" %% "minitest" % minitestVersion
  )

  // https://github.com/scodec/scodec-bits
  // 3-clause BSD
  val scodecBits = Seq(
    "org.scodec" %% "scodec-bits" % scodecVersion
  )

  // http://www.bouncycastle.org/java.html
  // MIT
  val bcpg = Seq(
    "org.bouncycastle" % "bcpg-jdk15on" % bcpgVersion
  )

  // http://logback.qos.ch/
  // EPL1.0 or LGPL 2.1
  val logback = Seq(
    "ch.qos.logback" % "logback-classic" % logbackVersion
  )

}
