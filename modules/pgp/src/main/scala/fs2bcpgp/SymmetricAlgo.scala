package fs2bcpgp

import org.bouncycastle.bcpg.{SymmetricKeyAlgorithmTags => Tags}

case class SymmetricAlgo(tag: Int)
object SymmetricAlgo {

  val Idea = SymmetricAlgo(Tags.IDEA)
  val DES3 = SymmetricAlgo(Tags.TRIPLE_DES)
  val CAST5 = SymmetricAlgo(Tags.CAST5)
  val Blowfish = SymmetricAlgo(Tags.BLOWFISH)
  val Safer = SymmetricAlgo(Tags.SAFER)
  val DES = SymmetricAlgo(Tags.DES)
  val AES128 = SymmetricAlgo(Tags.AES_128)
  val AES192 = SymmetricAlgo(Tags.AES_192)
  val AES256 = SymmetricAlgo(Tags.AES_256)
  val Twofish = SymmetricAlgo(Tags.TWOFISH)
  val Camellia128 = SymmetricAlgo(Tags.CAMELLIA_128)
  val Camellia192 = SymmetricAlgo(Tags.CAMELLIA_192)
  val Camellia256 = SymmetricAlgo(Tags.CAMELLIA_256)

  val all = Map(
    "AES128" -> AES128,
    "AES192" -> AES192,
    "AES256" -> AES256,
    "TWOFISH" -> Twofish,
    "BLOWFISH" -> Blowfish,
    "SAFER" -> Safer,
    "DES" -> DES,
    "DES3" -> DES3,
    "IDEA" -> Idea,
    "CAST5" -> CAST5,
    "CAMELLIA128" -> Camellia128,
    "CAMELLIA192" -> Camellia192,
    "CAMELLIA256" -> Camellia256
  )

  val allByTag = all.values.map(a => (a.tag, a)).toMap

  def find(algo: String): Option[SymmetricAlgo] = all.get(algo.toUpperCase)

  val default = Twofish
}
