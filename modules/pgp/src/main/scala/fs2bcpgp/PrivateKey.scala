package fs2bcpgp

import org.bouncycastle.openpgp.PGPPrivateKey
import scodec.bits.ByteVector

case class PrivateKey(key: PGPPrivateKey) {

  val keyId = key.getKeyID
  val id = ByteVector.fromLong(keyId).toHex

}
