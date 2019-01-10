package fs2bcpgp

import scala.collection.JavaConverters._
import scodec.bits.ByteVector
import cats.effect.Sync
import cats.implicits._

import org.bouncycastle.openpgp.{PGPSecretKeyRing, PGPSecretKeyRingCollection}
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

case class SecretKeys(ring: ByteVector) {

  def open[F[_]](implicit F: Sync[F]): F[PGPSecretKeyRingCollection] = F.delay {
    new PGPSecretKeyRingCollection(ring.toArray, new JcaKeyFingerprintCalculator())
  }

  def all[F[_]](implicit F: Sync[F]): F[List[Seckey]] =
    open.map(_.asScala.map(ring => Seckey(ring.getSecretKey)).toList)

  def find[F[_]](keyID: Long)(implicit F: Sync[F]): F[Option[Seckey]] =
    F.map(open)(k => Option(k.getSecretKey(keyID)).map(Seckey.apply))

  def findByUserID[F[_]](userID: String)(implicit F: Sync[F]): F[Option[Seckey]] =
    F.map(open)(k => k.asScala.filter(SecretKeys.seckeyOf(userID)).toList match {
      case h :: Nil => Some(Seckey(h.getSecretKey))
      case _ => None
    })

  def armored[F[_]](implicit F: Sync[F])=
    Keystore.toArmored(ring)

  def ++(other: SecretKeys): SecretKeys = SecretKeys(ring ++ other.ring)
}

object SecretKeys {

  def readArmored[F[_]](armored: String)(implicit F: Sync[F]): F[SecretKeys] =
    Keystore.fromArmored[F](armored).map(SecretKeys.apply)


  private def seckeyOf(id: String)(k: PGPSecretKeyRing): Boolean = {
    val sk = k.getSecretKey
    sk.getUserIDs.asScala.map(_.toString).filter(_ contains id).nonEmpty ||
      java.lang.Long.toHexString(sk.getKeyID).endsWith(id.toLowerCase)
  }
}
