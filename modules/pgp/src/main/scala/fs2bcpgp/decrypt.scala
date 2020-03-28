package fs2bcpgp

import java.io.{InputStream, OutputStream}
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.jcajce._
import org.bouncycastle.openpgp.operator.jcajce._
import org.bouncycastle.util.io.Streams
import ScalaCompat._

import cats.effect._
import cats.implicits._
import fs2._

object decrypt {
  private def unwrapPGP(clear: InputStream): InputStream = {
    val ld = {
      val fact = new JcaPGPObjectFactory(clear)
      fact.nextObject() match {
        case cd: PGPCompressedData =>
          val fact = new JcaPGPObjectFactory(cd.getDataStream)
          fact.nextObject().asInstanceOf[PGPLiteralData]
        case o =>
          o.asInstanceOf[PGPLiteralData]
      }
    }

    ld.getInputStream
  }

  private def readPGP[F[_]: Sync: ContextShift](
      bufferSize: Int,
      blocker: Blocker
  ): Pipe[F, InputStream, Byte] =
    _.flatMap { clear =>
      val unc = unwrapPGP(clear)
      io.readInputStream(Sync[F].pure(unc), bufferSize, blocker)
    }

  private def makeEncData[A <: PGPEncryptedData](in: InputStream): Iterator[A] = {
    val pgpf = new JcaPGPObjectFactory(in)
    val enc = pgpf.nextObject() match {
      case d: PGPEncryptedDataList => d
      case _                       => pgpf.nextObject().asInstanceOf[PGPEncryptedDataList]
    }
    enc.toScala.map(_.asInstanceOf[A]).iterator
  }

  private def encryptedData[F[_]: ConcurrentEffect, A <: PGPEncryptedData]
      : Pipe[F, Byte, A] =
    _.through(io.toInputStream).map(makeEncData[A]).flatMap(it => Stream.fromIterator(it))

  def pubkeySync[F[_]](
      in: InputStream,
      ks: Keystore,
      pass: Long => Array[Char],
      out: OutputStream
  )(implicit F: Sync[F]): F[Unit] = {
    def getKey: Long => F[PrivateKey] =
      id =>
        ks.secret.find(id).flatMap {
          case Some(k) => k.extractPrivateKey(pass(id))
          case None    => F.raiseError(new Exception(s"No private key found for id $id"))
        }

    val dec: F[Iterator[F[Unit]]] = F.delay {
      makeEncData[PGPPublicKeyEncryptedData](in)
        .map { pgpobj =>
          getKey(pgpobj.getKeyID)
            .map(pk =>
              pgpobj.getDataStream(
                new JcePublicKeyDataDecryptorFactoryBuilder()
                  .setProvider(Provider.name)
                  .build(pk.key)
              )
            )
            .map(unwrapPGP)
        }
        .map(_.map(Streams.pipeAll(_, out)))
    }

    dec.flatMap(a => a.foldLeft(F.pure(())) { case (l, r) => l.flatMap(_ => r) })
  }

  def symmetricSync[F[_]](in: InputStream, pass: Array[Char], out: OutputStream)(
      implicit F: Sync[F]
  ): F[Unit] = {
    val dec: F[Iterator[Unit]] = F.delay {
      makeEncData[PGPPBEEncryptedData](in)
        .map { pgpobj =>
          unwrapPGP(
            pgpobj.getDataStream(
              new JcePBEDataDecryptorFactoryBuilder(
                new JcaPGPDigestCalculatorProviderBuilder()
                  .setProvider(Provider.name)
                  .build()
              ).setProvider(Provider.name)
                .build(pass)
            )
          )
        }
        .map(Streams.pipeAll(_, out))
    }

    dec.map(a => a.foldLeft(()) { case (_, r) => r })
  }

  def pubkey[F[_]: ConcurrentEffect: RaiseThrowable: ContextShift](
      ks: Keystore,
      pass: Long => Array[Char],
      blocker: Blocker
  ): Pipe[F, Byte, Byte] = in => {
    val clearText: Stream[F, InputStream] =
      in.through(encryptedData[F, PGPPublicKeyEncryptedData]).flatMap { pgpobj =>
        Stream.eval(ks.secret.find(pgpobj.getKeyID)).flatMap {
          case Some(k) =>
            Stream
              .eval(k.extractPrivateKey(pass(pgpobj.getKeyID)))
              .map(privKey =>
                pgpobj.getDataStream(
                  new JcePublicKeyDataDecryptorFactoryBuilder()
                    .setProvider(Provider.name)
                    .build(privKey.key)
                )
              )
          case None =>
            Stream.raiseError(
              new Exception(s"No private key found for id: ${pgpobj.getKeyID}")
            )
        }
      }

    clearText.through(readPGP(8192, blocker))
  }

  def symmetric[F[_]: ConcurrentEffect: ContextShift](
      pass: Array[Char],
      blocker: Blocker
  ): Pipe[F, Byte, Byte] = in => {
    val clearText: Stream[F, InputStream] =
      in.through(encryptedData[F, PGPPBEEncryptedData]).evalMap { pgpobj =>
        Sync[F].delay(
          pgpobj.getDataStream(
            new JcePBEDataDecryptorFactoryBuilder(
              new JcaPGPDigestCalculatorProviderBuilder()
                .setProvider(Provider.name)
                .build()
            ).setProvider(Provider.name)
              .build(pass)
          )
        )
      }

    clearText.through(readPGP(8192, blocker))
  }
}
