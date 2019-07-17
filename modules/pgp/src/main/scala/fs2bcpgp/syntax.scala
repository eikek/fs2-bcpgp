package fs2bcpgp

import java.nio.charset.{Charset, StandardCharsets}
import java.io.{InputStream, OutputStream, ByteArrayInputStream, ByteArrayOutputStream}
import fs2._
import cats.effect._
import cats.implicits._
import scodec.bits.ByteVector

object syntax {

  implicit class StreamCrypt[F[_]](s: Stream[F, Byte]) {
    def encryptPubkey(key: Pubkey, chunkSize: Int, blocker: Blocker)
      (implicit F: ConcurrentEffect[F], CS: ContextShift[F]): Stream[F, Byte] =
      s.through(encrypt.pubkey(key, chunkSize, blocker))

    def decryptPubkey(ks: Keystore, pass: Long => Array[Char], blocker: Blocker)
      (implicit F: ConcurrentEffect[F], ev: RaiseThrowable[F], CS: ContextShift[F]): Stream[F, Byte] =
      s.through(decrypt.pubkey(ks, pass, blocker))

    def encryptSymmetric(pass: Array[Char], chunkSize: Int, blocker: Blocker, algo: SymmetricAlgo = SymmetricAlgo.default)
      (implicit F: ConcurrentEffect[F], CS: ContextShift[F]): Stream[F, Byte] =
      s.through(encrypt.symmetric(algo, pass, chunkSize, blocker))

    def decryptSymmetric(pass: Array[Char], blocker: Blocker)
      (implicit F: ConcurrentEffect[F], CS: ContextShift[F]): Stream[F, Byte] =
      s.through(decrypt.symmetric(pass, blocker))
  }


  private def pipeStreams[F[_], A, B]
    (f: (InputStream, OutputStream) => F[Unit], to: A => Array[Byte], from: Array[Byte] => B)
    (implicit F: Sync[F]): A => F[B] = a => {
    val inout = F.delay {
        (new ByteArrayInputStream(to(a)), new ByteArrayOutputStream())
      }
      inout.flatMap { case (in, out) =>
        f(in, out).map { _ =>
          in.close()
          out.close()
          from(out.toByteArray)
        }
      }
  }

  implicit class StringCrypt(s: String) {
    private def encryptStreams[F[_]](f: (InputStream, OutputStream) => F[Unit], charset: Charset)(implicit F: Sync[F]): F[String] =
      pipeStreams[F, String, String](f, s => s.getBytes(charset), ba => ByteVector.view(ba).toBase64).apply(s)

    private def decryptStreams[F[_]](f: (InputStream, OutputStream) => F[Unit], charset: Charset)(implicit F: Sync[F]): F[String] =
      pipeStreams[F, String, String](f, s => ByteVector.fromValidBase64(s).toArray, ba => new String(ba, charset)).apply(s)

    def encryptPubkey[F[_]](key: Pubkey, charset: Charset = StandardCharsets.UTF_8)(implicit F: Sync[F]): F[String] =
      encryptStreams(encrypt.pubkeySync(_, key, _), charset)

    def encryptSymmetric[F[_]](pass: Array[Char]
      , algo: SymmetricAlgo = SymmetricAlgo.default
      , charset: Charset = StandardCharsets.UTF_8)(implicit F: Sync[F]): F[String] =
      encryptStreams(encrypt.symmetricSync(_, algo, pass, _), charset)

    def decryptPubkey[F[_]](ks: Keystore, pass: Long => Array[Char], charset: Charset = StandardCharsets.UTF_8)(implicit F: Sync[F]): F[String] =
      decryptStreams(decrypt.pubkeySync(_, ks, pass, _), charset)

    def decryptSymmetric[F[_]](pass: Array[Char], charset: Charset = StandardCharsets.UTF_8)(implicit F: Sync[F]): F[String] =
      decryptStreams(decrypt.symmetricSync(_, pass, _), charset)
  }

  implicit class ByteVectorCrypt(bv: ByteVector) {

    def encryptPubkey[F[_]](key: Pubkey)(implicit F: Sync[F]): F[ByteVector] =
      pipeStreams[F, ByteVector, ByteVector](
        encrypt.pubkeySync[F](_, key, _)
          , _.toArray
          , ByteVector.view(_)).apply(bv)

    def encryptSymmetric[F[_]](pass: Array[Char], algo: SymmetricAlgo = SymmetricAlgo.default)(implicit F: Sync[F]): F[ByteVector] =
      pipeStreams[F, ByteVector, ByteVector](
        encrypt.symmetricSync(_, algo, pass, _)
          , _.toArray
          , ByteVector.view(_)).apply(bv)

    def decryptPubkey[F[_]](ks: Keystore, pass: Long => Array[Char])(implicit F: Sync[F]): F[ByteVector] =
      pipeStreams[F, ByteVector, ByteVector](
        decrypt.pubkeySync(_, ks, pass, _)
          , _.toArray
          , ByteVector.view(_)).apply(bv)

    def decryptSymmetric[F[_]](pass: Array[Char])(implicit F: Sync[F]): F[ByteVector] =
      pipeStreams[F, ByteVector, ByteVector](
        decrypt.symmetricSync(_, pass, _)
          , _.toArray
          , ByteVector.view(_)).apply(bv)
  }
}
