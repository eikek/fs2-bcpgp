package fs2bcpgp

import ScalaCompat._
import cats.effect.Sync
import scodec.bits.ByteVector

import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder

case class Seckey(psk: PGPSecretKey) {

  val keyId = psk.getKeyID
  val id = ByteVector.fromLong(keyId).toHex

  val userIDs = psk.getUserIDs.toScala.toList

  def pubKey = Pubkey(psk.getPublicKey)

  def extractPrivateKey[F[_]](pass: Array[Char])(implicit F: Sync[F]): F[PrivateKey] =
    F.delay {
      PrivateKey(
        psk.extractPrivateKey(
          new JcePBESecretKeyDecryptorBuilder().setProvider(Provider.name).build(pass)
        )
      )
    }

}
