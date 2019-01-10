package fs2bcpgp

import java.time.{Duration, Instant}
import org.bouncycastle.openpgp._ //PGPPublicKey
import scala.collection.JavaConverters._
import scodec.bits.ByteVector
import org.bouncycastle.bcpg.SignatureSubpacketTags
import org.bouncycastle.bcpg.sig.PreferredAlgorithms
import cats.effect.Sync

case class Pubkey(pbk: PGPPublicKey) {

  val keyId = pbk.getKeyID
  val id = ByteVector.fromLong(keyId).toHex

  val fingerprint = ByteVector.view(pbk.getFingerprint)

  val algorithm = AsymmetricAlgo.allByTag.get(pbk.getAlgorithm).getOrElse(AsymmetricAlgo(pbk.getAlgorithm, ""))

  val strength = pbk.getBitStrength

  val version = pbk.getVersion

  val created = Instant.ofEpochMilli(pbk.getCreationTime.getTime)

  val validity = Duration.ofSeconds(pbk.getValidSeconds)

  val userIDs = pbk.getUserIDs.asScala.toList

  val isEncryptionKey = pbk.isEncryptionKey
  val isMasterKey = pbk.isMasterKey

  val isRevoked = pbk.hasRevocation

  lazy val preferedAlgorithms: List[SymmetricAlgo] = {
    for {
      sig <- pbk.getSignatures.asScala.asInstanceOf[Iterator[PGPSignature]]
      pck <- sig.getHashedSubPackets().getSubpacket(SignatureSubpacketTags.PREFERRED_SYM_ALGS) match {
        case pa: PreferredAlgorithms =>
          pa.getPreferences.toList.map(SymmetricAlgo.allByTag.get).collect({case Some(x) => x })
      }
    } yield pck
  }.toList

  def armored[F[_]: Sync]: F[String] =
    Keystore.toArmored(ByteVector.view(pbk.getEncoded))

}
