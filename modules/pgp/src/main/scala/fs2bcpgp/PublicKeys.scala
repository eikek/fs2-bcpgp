package fs2bcpgp

import scala.jdk.CollectionConverters._
import scodec.bits.ByteVector
import cats.effect.Sync
import cats.data.NonEmptyList
import cats.implicits._

import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

case class PublicKeys(ring: ByteVector) {

  def open[F[_]](implicit F: Sync[F]): F[PGPPublicKeyRingCollection] = F.delay {
    new PGPPublicKeyRingCollection(ring.toArray, new JcaKeyFingerprintCalculator())
  }

  def all[F[_]](implicit F: Sync[F]): F[NonEmptyList[Pubkey]] =
    open.map(_.asScala.
      map(ring => Pubkey(ring.getPublicKey)).toList).
      map(NonEmptyList.fromList).
      map(_.getOrElse(sys.error("Empty public keyring")))

  def find[F[_]](keyID: Long)(implicit F: Sync[F]): F[Option[Pubkey]] =
    F.map(open)(k => Option(k.getPublicKey(keyID)).map(Pubkey.apply))

  def findByUserID[F[_]](userID: String)(implicit F: Sync[F]): F[Option[Pubkey]] =
    F.map(open)(k => k.asScala.filter(PublicKeys.pubkeyOf(userID)).toList match {
      case h :: Nil => Some(Pubkey(h.getPublicKey))
      case _ => None
    })

  def armored[F[_]](implicit F: Sync[F])=
    Keystore.toArmored(ring)


  def ++(other: PublicKeys): PublicKeys = PublicKeys(ring ++ other.ring)
}

object PublicKeys {
  private def pubkeyOf(id: String)(k: PGPPublicKeyRing): Boolean = {
    val pk = k.getPublicKey
    pk.getUserIDs.asScala.map(_.toString).filter(_ contains id).nonEmpty ||
    java.lang.Long.toHexString(pk.getKeyID).endsWith(id.toLowerCase)
  }

  def readArmored[F[_]](armored: String)(implicit F: Sync[F]): F[PublicKeys] =
    Keystore.fromArmored[F](armored).map(PublicKeys.apply)
}
