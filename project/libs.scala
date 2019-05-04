import sbt._

object libs {

  val `scala-version` = "2.12.8"

  // https://github.com/typelevel/cats
  // MIT http://opensource.org/licenses/mit-license.php
  val `cats-core` = "org.typelevel" %% "cats-core" % "1.6.0"

  // https://github.com/typelevel/cats-effect
  // Apache 2.0
  val `cats-effect` = "org.typelevel" %% "cats-effect" % "1.3.0"

  // https://github.com/functional-streams-for-scala/fs2
  // MIT
  val `fs2-core` = "co.fs2" %% "fs2-core" % "1.0.4"
  val `fs2-io` = "co.fs2" %% "fs2-io" % "1.0.4"

  // https://github.com/monix/minitest
  // Apache 2.0
  val minitest = "io.monix" %% "minitest" % "2.4.0"
  val `minitest-laws` = "io.monix" %% "minitest-laws" % "2.4.0"

  // https://github.com/rickynils/scalacheck
  // unmodified 3-clause BSD
  // val scalacheck = "org.scalacheck" %% "scalacheck" % "1.13.5"

  // https://github.com/scodec/scodec-bits
  // 3-clause BSD
  val `scodec-bits` = "org.scodec" %% "scodec-bits" % "1.1.10"

  // http://www.bouncycastle.org/java.html
  // MIT
  val bcpg = "org.bouncycastle" % "bcpg-jdk15on" % "1.61"

  // http://logback.qos.ch/
  // EPL1.0 or LGPL 2.1
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.3"

}
