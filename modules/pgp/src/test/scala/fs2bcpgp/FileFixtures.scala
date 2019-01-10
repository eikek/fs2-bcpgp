package fs2bcpgp

import fs2.io
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.collection.JavaConverters._
import cats.effect.IO

import minitest.api.Asserts

trait FileFixtures {
  self: Asserts =>

  def withNonExistingFile[A](code: Path => IO[A]): IO[A] = {
    val prefix = Paths.get("target")
    val file = prefix.resolve(s"test-file-${UUID.randomUUID().toString}")
    assert(fileNotExists(file))
    val prg = for {
      a <- code(file).attempt
      _ <- IO(Files.deleteIfExists(file))
    } yield a
    prg.flatMap(_.fold(IO.raiseError, IO.pure))
  }

  def withExistingFile[A](code: Path => IO[A]): IO[A] = {
    withNonExistingFile { file =>
      IO(Files.createFile(file)).flatMap(_ => code(file))
    }
  }

  def withFileContents[A](cnt: String)(code: Path => IO[A]): IO[A] = {
    withNonExistingFile { file =>
      IO(Files.write(file, cnt.getBytes("UTF-8"))).
        flatMap(_ => IO(assertFileNonEmpty(file))).
        flatMap(_ => code(file))
    }
  }

  def fileHash(f: Path): IO[String] =
    io.file.readAll[IO](f, 96 * 1024).
      through(fs2.hash.sha1).
      map(b => "%x".format(b)).
      fold1(_ ++ _).
      compile.last.
      map(_.get)


  def fileSize(f: Path): Long = Files.size(f)

  def fileExits(f: Path): Boolean = Files.exists(f)

  def fileNotExists(f: Path): Boolean = !fileExits(f)

  def fileNonEmpty(f: Path): Boolean = fileExits(f) && fileSize(f) > 0

  def fileWrite(f: Path, content: String): Unit =
    Files.write(f, content.getBytes("UTF-8"))

  def assertFileNonEmpty(f: Path): Unit = {
    assert(fileExits(f), s"File $f expected to be non empty, but it doesn't exist")
    assert(fileSize(f) > 0, s"File $f expected to be non empty, but it's length is ${fileSize(f)}")
  }

  def fileContentsUtf8(f: Path): String =
    Files.readAllLines(f).asScala.mkString("\n")
}
