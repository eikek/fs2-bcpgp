package fs2bcpgp

import minitest._
import cats.effect.IO
import fs2.{io, text, Stream, Pipe, Chunk}

import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.file.Path
import java.io._

object EncryptSpec extends SimpleTestSuite with FileFixtures {
  val chunkSize = 8192

  def loadKeystore: IO[Keystore] =
    Keystore.fromInputStream(IO(getClass.getResource("/all.kr").openStream))

  def asString: Pipe[IO, Byte, String] =
    _.through(text.utf8Decode).
      chunks.map(_.toVector.mkString).fold1(_ + _)

  test ("encrypt stream with public key") {
    val ks = loadKeystore.unsafeRunSync
    val key = ks.public.findByUserID[IO]("test").unsafeRunSync.get

    val in = Stream.emits("hello world".getBytes).covary[IO]

    val org = in.through(encrypt.pubkey[IO](key, chunkSize)).
      through(decrypt.pubkey[IO](ks, _ => "test".toCharArray)).
      through(asString).
      compile.last.unsafeRunSync

    assertEquals(org, Some("hello world"))
  }

  test("pubkey encrypt stream of larger file") {
    val ks = loadKeystore.unsafeRunSync
    val key = ks.public.findByUserID[IO]("test").unsafeRunSync.get

    // generate some „large” file
    def createFile(f: Path) =
      Stream.iterate(0)(_ + 1).covary[IO].
        map(i => Vector.fill(i)(i.toString).mkString).
        intersperse("\n").
        take(800L).
        through(text.utf8Encode).
        to(io.file.writeAll[IO](f)).
        compile.drain

    // encrypt
    def encryptFile(from: Path, to: Path) =
      io.file.readAll[IO](from, 96 * 1024).
        through(encrypt.pubkey[IO](key, chunkSize)).
        to(io.file.writeAll[IO](to)).
        compile.drain

    // decrypt
    def decryptFile(from: Path, to: Path) =
      io.file.readAll[IO](from, 96 * 1024).
        through(decrypt.pubkey[IO](ks, _ => "test".toCharArray)).
        to(io.file.writeAll[IO](to)).
        compile.drain

    val test = withNonExistingFile { f1 =>
      withNonExistingFile { f2 =>
        withNonExistingFile { f3 =>
          for {
            _     <- createFile(f1)
            _     <- IO(println(s"Created file ${f1} with size ${fileSize(f1)}"))
            _     <- encryptFile(f1, f2)
            _     <- decryptFile(f2, f3)
            ho    <- fileHash(f1)
            hd    <- fileHash(f3)
            _     <- IO(assertEquals(ho, hd))
          } yield ()
        }
      }
    }

    test.unsafeRunSync

  }

  test ("encrypt stream symmetric") {
    val in = Stream.emits("hello world".getBytes).covary[IO]
    val pass = "hahaha".toCharArray
    val org = in.through(encrypt.symmetric(SymmetricAlgo.AES256, pass, chunkSize)).
      through(decrypt.symmetric(pass)).
      through(asString).
      compile.last.unsafeRunSync

    assertEquals(org, Some("hello world"))
  }

  test ("pk encrypt java.io") {
    val ks = loadKeystore.unsafeRunSync
    val key = ks.public.findByUserID[IO]("test").unsafeRunSync.get

    val in = new ByteArrayInputStream("hello world".getBytes)
    val out = new ByteArrayOutputStream()
    encrypt.pubkeySync[IO](in, key, out).unsafeRunSync
    val org = Stream.chunk(Chunk.bytes(out.toByteArray)).covary[IO].
      through(decrypt.pubkey[IO](ks, _ => "test".toCharArray)).
      through(asString).
      compile.last.unsafeRunSync
    assertEquals(org, Some("hello world"))
  }

  test ("symmetric encrypt java.io") {
    val in = new ByteArrayInputStream("hello world".getBytes)
    val out = new ByteArrayOutputStream()
    val pass = "hahaha".toCharArray
    encrypt.symmetricSync[IO](in, SymmetricAlgo.Twofish, pass, out).unsafeRunSync
    val org = Stream.chunk(Chunk.bytes(out.toByteArray)).covary[IO].
      through(decrypt.symmetric(pass)).
      through(asString).
      compile.last.unsafeRunSync
    assertEquals(org, Some("hello world"))
  }

  test ("pubkey decrypt java.io") {
    val ks = loadKeystore.unsafeRunSync
    val key = ks.public.findByUserID[IO]("test").unsafeRunSync.get

    val in = new ByteArrayInputStream("hello world".getBytes)
    val out = new ByteArrayOutputStream()
    encrypt.pubkeySync[IO](in, key, out).unsafeRunSync
    val encIn = new ByteArrayInputStream(out.toByteArray)

    out.reset()
    decrypt.pubkeySync[IO](encIn, ks, _ => "test".toCharArray, out).unsafeRunSync
    val org = new String(out.toByteArray, "UTF-8")
    assertEquals(org, "hello world")
  }

  test ("symmetric decrypt java.io") {
    val in = new ByteArrayInputStream("hello world".getBytes)
    val out = new ByteArrayOutputStream()
    val pass = "hahaha".toCharArray
    encrypt.symmetricSync[IO](in, SymmetricAlgo.Twofish, pass, out).unsafeRunSync
    val encIn = new ByteArrayInputStream(out.toByteArray)

    out.reset()
    decrypt.symmetricSync[IO](encIn, "hahaha".toCharArray, out).unsafeRunSync
    val org = new String(out.toByteArray, "UTF-8")
    assertEquals(org, "hello world")
  }
}
