package fs2bcpgp

import java.io.{InputStream, OutputStream}
import java.security.SecureRandom
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce._
import org.bouncycastle.util.io.Streams

import cats.implicits._
import cats.effect._
import fs2._
import fs2.concurrent.Queue

object encrypt {

  private def makeBuffer = new Array[Byte](1 << 8)

  private def makePKEncryptGenerator(key: Pubkey): PGPEncryptedDataGenerator = {
    val algo = key.preferedAlgorithms.headOption.getOrElse(SymmetricAlgo.CAST5)
    val encGen = new PGPEncryptedDataGenerator(
      new JcePGPDataEncryptorBuilder(algo.tag)
        .setWithIntegrityPacket(true)
        .setSecureRandom(new SecureRandom())
        .setProvider(Provider.name)
    )

    encGen.addMethod(
      new JcePublicKeyKeyEncryptionMethodGenerator(key.pbk).setProvider(Provider.name)
    )
    encGen
  }

  private def makePBEEncryptGenerator(
      algo: SymmetricAlgo,
      pass: Array[Char]
  ): PGPEncryptedDataGenerator = {
    val encGen = new PGPEncryptedDataGenerator(
      new JcePGPDataEncryptorBuilder(algo.tag)
        .setWithIntegrityPacket(true)
        .setSecureRandom(new SecureRandom())
        .setProvider(Provider.name)
    )

    encGen.addMethod(
      new JcePBEKeyEncryptionMethodGenerator(pass).setProvider(Provider.name)
    )
    encGen
  }

  def pubkeySync[F[_]](
      in: InputStream,
      key: Pubkey,
      out: OutputStream,
      name: String = ""
  )(implicit F: Sync[F]): F[Unit] =
    encryptSync(F.delay(makePKEncryptGenerator(key)), in, out, name)

  def symmetricSync[F[_]: Sync](
      in: InputStream,
      algo: SymmetricAlgo,
      pass: Array[Char],
      out: OutputStream,
      name: String = ""
  ): F[Unit] =
    encryptSync(Sync[F].delay(makePBEEncryptGenerator(algo, pass)), in, out, name)

  private def encryptSync[F[_]](
      encGen: F[PGPEncryptedDataGenerator],
      in: InputStream,
      out: OutputStream,
      name: String
  )(implicit F: Sync[F]): F[Unit] = {
    val cout = encGen.map(_.open(out, makeBuffer))
    val literalGen = new PGPLiteralDataGenerator()
    Stream
      .bracket(cout)(c => F.delay(c.close()))
      .flatMap(c =>
        Stream
          .bracket(
            F.delay(
              literalGen
                .open(c, PGPLiteralData.BINARY, name, new java.util.Date(), makeBuffer)
            )
          )(p => F.delay(p.close()))
          .flatMap(p => Stream.eval(F.delay(Streams.pipeAll(in, p))))
      )
      .compile
      .drain
  }

  def pubkey[F[_]](key: Pubkey, chunkSize: Int, blocker: Blocker, name: String = "")(
      implicit F: ConcurrentEffect[F],
      CS: ContextShift[F]
  ): Pipe[F, Byte, Byte] =
    encryptWith[F](makePKEncryptGenerator(key), chunkSize, blocker, name)

  def symmetric[F[_]: ConcurrentEffect: ContextShift](
      algo: SymmetricAlgo,
      pass: Array[Char],
      chunkSize: Int,
      blocker: Blocker,
      name: String = ""
  ): Pipe[F, Byte, Byte] =
    encryptWith(makePBEEncryptGenerator(algo, pass), chunkSize, blocker, name)

  private def encryptWith[F[_]](
      encGen: PGPEncryptedDataGenerator,
      chunkSize: Int,
      blocker: Blocker,
      name: String
  )(implicit F: ConcurrentEffect[F], CS: ContextShift[F]): Pipe[F, Byte, Byte] = in => {
    Stream.eval(Queue.synchronous[F, Option[Chunk[Byte]]]).flatMap { q =>
      val outs = F.delay {
        val out = new OutputStream {
          var chunk: Chunk[Byte] = Chunk.empty

          private def addChunkSync(c: Option[Chunk[Byte]]): Unit =
            Effect[F].toIO(q.enqueue1(c)).unsafeRunSync

          @annotation.tailrec
          private def addChunk(c: Chunk[Byte]): Unit = {
            val free = chunkSize - chunk.size
            if (c.size > free) {
              addChunkSync(Some(Chunk.concat(Seq(chunk, c.take(free)))))
              chunk = Chunk.empty
              addChunk(c.drop(free))
            } else {
              chunk = Chunk.concat(Seq(chunk, c))
            }
          }

          override def close(): Unit = {
            addChunkSync(Some(chunk))
            chunk = Chunk.empty
            addChunkSync(None)
          }

          override def write(bytes: Array[Byte]): Unit =
            addChunk(Chunk.bytes(bytes))
          override def write(bytes: Array[Byte], off: Int, len: Int): Unit =
            addChunk(Chunk.bytes(bytes, off, len))
          override def write(b: Int): Unit =
            addChunk(Chunk.singleton(b.toByte))
        }

        val literalGen = new PGPLiteralDataGenerator()
        val cout = encGen.open(out, makeBuffer)
        val pout = literalGen
          .open(cout, PGPLiteralData.BINARY, name, new java.util.Date(), makeBuffer)
        (out, cout, pout)
      }

      def write =
        Stream
          .bracket(outs)({
            case (out, cout, pout) =>
              F.delay {
                pout.close()
                cout.close()
                out.close()
              }
          })
          .flatMap({
            case (_, _, pout) =>
              in.through(
                io.writeOutputStream(F.pure(pout), blocker, closeAfterUse = false)
              )
          })

      Stream.suspend {
        q.dequeue.unNoneTerminate
          .flatMap(Stream.chunk(_))
          .concurrently(write)
      }
    }
  }
}
