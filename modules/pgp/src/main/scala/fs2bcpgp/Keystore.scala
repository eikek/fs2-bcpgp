package fs2bcpgp

import scodec.bits.ByteVector
import cats.effect._
import cats.Traverse
import cats.implicits._
import fs2._
import scala.concurrent.ExecutionContext

import scala.collection.JavaConverters._
import java.nio.file.{Files, Path}
import java.io.{InputStream, ByteArrayInputStream, ByteArrayOutputStream}
import java.security.SecureRandom
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.bcpg.{ArmoredInputStream, ArmoredOutputStream}
import org.bouncycastle.bcpg.sig.{KeyFlags, Features}
import org.bouncycastle.openpgp._
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp.operator.bc._

case class Keystore(public: PublicKeys, secret: SecretKeys) {

  def armored[F[_]: Sync]: F[String] =
    for {
      sa <- secret.armored
      pa <- public.armored
    } yield pa + sa

  def addPublicKey(pk: PublicKeys): Keystore =
    Keystore(public ++ pk, secret)

  def addSecretKey(sk: SecretKeys): Keystore =
    Keystore(public, secret ++ sk)

  def addNewKeypair[F[_]](userID: String, pass: Array[Char], strength: Int = 4096)(implicit F: Sync[F]): F[Keystore] =
    Keystore.generate[F](userID, pass, strength).map(ks => this ++ ks)

  def ++(other: Keystore): Keystore = Keystore(public ++ other.public, secret ++ other.secret)

  def checkPassword[F[_]](pw: Long => Array[Char])(implicit F: Sync[F]): F[List[(Long, Boolean)]] =
    secret.all[F].flatMap { keys =>
      Traverse[List].sequence(keys.map { k =>
        k.extractPrivateKey(pw(k.keyId)).
          map(_ => (k.keyId, true)).
          handleError(_ => (k.keyId, false))
      })
    }
}

object Keystore {
  // https://bouncycastle-pgp-cookbook.blogspot.de/2013_01_01_archive.html
  def generate[F[_]: Sync](userID: String, pass: Array[Char], strength: Int = 4096): F[Keystore] = Sync[F].delay {
    val kpg = new RSAKeyPairGenerator()
    kpg.init(new RSAKeyGenerationParameters(
      java.math.BigInteger.valueOf(65537), //exponent
      new SecureRandom,
      strength, // key strength
      12)) // certanity

    val pubkey = new BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, kpg.generateKeyPair(), new java.util.Date())

    // Add a self-signature on the id
    val signhashgen = new PGPSignatureSubpacketGenerator()

    // Add signed metadata on the signature.
    // 1) Declare its purpose
    signhashgen.setKeyFlags(false, KeyFlags.SIGN_DATA|KeyFlags.CERTIFY_OTHER|KeyFlags.ENCRYPT_COMMS|KeyFlags.ENCRYPT_STORAGE)
    // 2) Set preferences for secondary crypto algorithms to use
    //    when sending messages to this key.
    signhashgen.setPreferredSymmetricAlgorithms(false, Array(
      SymmetricAlgo.Twofish,
      SymmetricAlgo.AES256,
      SymmetricAlgo.AES192,
      SymmetricAlgo.AES128
    ).map(_.tag))
    signhashgen.setPreferredHashAlgorithms(false, Array[Int](
      HashAlgorithmTags.SHA256,
      HashAlgorithmTags.SHA1,
      HashAlgorithmTags.SHA384,
      HashAlgorithmTags.SHA512,
      HashAlgorithmTags.SHA224,
    ))
    // 3) Request senders add additional checksums to the
    //    message (useful when verifying unsigned messages.)
    signhashgen.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION)

    // Objects used to encrypt the secret key.
    val sha1Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
    val sha256Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256)

    val pske = (new BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha256Calc, 192))
        .build(pass);

    // Finally, create the keyring itself. The constructor
    // takes parameters that allow it to generate the self
    // signature.
    val keyRingGen = new PGPKeyRingGenerator(
      PGPSignature.POSITIVE_CERTIFICATION,
      pubkey,
      userID,
      sha1Calc,
      signhashgen.generate(),
      null,
      new BcPGPContentSignerBuilder(pubkey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1),
      pske)

    val pub = keyRingGen.generatePublicKeyRing().getEncoded()
    val sec = keyRingGen.generateSecretKeyRing().getEncoded()

    Keystore(PublicKeys(ByteVector.view(pub)), SecretKeys(ByteVector.view(sec)))
  }

  def fromInputStream[F[_]: Sync : ContextShift](in: F[InputStream]
    , blockingEc: ExecutionContext
    , closeAfterUse: Boolean = true): F[Keystore] =
    io.readInputStream[F](in, 8192, blockingEc, closeAfterUse).
      through(text.utf8Decode).
      through(text.lines).
      compile.toVector.flatMap(fromArmoredLines[F])

  def fromArmoredFile[F[_]](f: Path)(implicit F: Sync[F]): F[Keystore] =
    F.delay(Files.readAllLines(f).asScala).
      flatMap(lines => fromArmoredLines(lines))

  def fromArmoredString[F[_]: Sync](armored: String): F[Keystore] =
    fromArmoredLines(armored.split("\\r?\\n"))

  private def fromArmoredLines[F[_]: Sync](lines: Iterable[String]): F[Keystore] = {
    val (pub, sec, _, _) = lines.foldLeft(("", "", false, false)) { case ((publines, seclines, pub, sec), line) =>
      line match {
        case "-----BEGIN PGP PRIVATE KEY BLOCK-----" =>
          (publines, line, false, true)
        case "-----END PGP PRIVATE KEY BLOCK-----" =>
          (publines, seclines + "\n" + line, false, false)
        case "-----BEGIN PGP PUBLIC KEY BLOCK-----" =>
          (line, seclines, true, false)
        case "-----END PGP PUBLIC KEY BLOCK-----" =>
          (publines +"\n"+ line, seclines, false, false)
        case _ if sec =>
          (publines, seclines +"\n"+ line, pub, sec)
        case _ if pub =>
          (publines +"\n"+ line, seclines, pub, sec)
        case _ =>
          (publines, seclines, pub, sec)
      }
    }
    val ks = for {
      p <- PublicKeys.readArmored(pub)
      s <- SecretKeys.readArmored(sec)
    } yield Keystore(p, s)
    ks.ensure(new Exception(s"No public key available"))(_.public.ring.nonEmpty).
      ensure(new Exception(s"No secret key available"))(_.secret.ring.nonEmpty)
  }


  private[fs2bcpgp] def toArmored[F[_]](bytes: ByteVector)(implicit F: Sync[F])= F.delay {
    val buffer = new ByteArrayOutputStream()
    val aout = new ArmoredOutputStream(buffer)
    aout.write(bytes.toArray)
    aout.close
    new String(buffer.toByteArray, "UTF-8")
  }

  private[fs2bcpgp] def fromArmored[F[_]](armored: String)(implicit F: Sync[F]): F[ByteVector] =
    F.delay {
      val in = new ArmoredInputStream(new ByteArrayInputStream(armored.getBytes("UTF-8")))
      @annotation.tailrec
      def go(buf: Array[Byte], result: ByteVector): ByteVector =
        in.read(buf, 0, buf.length) match {
          case -1 => result
          case n => go(buf, result ++ ByteVector(buf, 0, n))
        }

      go(new Array[Byte](8 * 1024), ByteVector.empty)
    }
}
