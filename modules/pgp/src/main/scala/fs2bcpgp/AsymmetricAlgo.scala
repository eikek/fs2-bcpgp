package fs2bcpgp

import org.bouncycastle.bcpg.{PublicKeyAlgorithmTags => Tags}

case class AsymmetricAlgo(tag: Int, family: String)
object AsymmetricAlgo {

  val DiffieHellman = AsymmetricAlgo(Tags.DIFFIE_HELLMAN, "DH")
  val DSA = AsymmetricAlgo(Tags.DSA, "DSA")
  val ECDH = AsymmetricAlgo(Tags.ECDH, "DH")
  val ECDSA = AsymmetricAlgo(Tags.ECDSA, "DSA")
  val ElgamalEncrypt = AsymmetricAlgo(Tags.ELGAMAL_ENCRYPT, "ElGamal")
  val ElgamalGeneral = AsymmetricAlgo(Tags.ELGAMAL_GENERAL, "ElGamal")
  val RsaEncrypt = AsymmetricAlgo(Tags.RSA_ENCRYPT, "RSA")
  val RsaGeneral = AsymmetricAlgo(Tags.RSA_GENERAL, "RSA")
  val RsaSign = AsymmetricAlgo(Tags.RSA_SIGN, "RSA")

  val all = Map(
    "DIFFIEHELLMAN" -> DiffieHellman,
    "DSA" -> DSA,
    "ECDH" -> ECDH,
    "ECDSA" -> ECDSA,
    "ELGAMALENCRYPT" -> ElgamalEncrypt,
    "ELGAMALGENERAL" -> ElgamalGeneral,
    "RSAENCRYPT" -> RsaEncrypt,
    "RSAGENERAL" -> RsaGeneral,
    "RSASIGN" -> RsaSign
  )

  val allByTag = all.values.map(a => (a.tag, a)).toMap

  def find(algo: String): Option[AsymmetricAlgo] = all.get(algo.toUpperCase)

  val default = RsaGeneral
}
