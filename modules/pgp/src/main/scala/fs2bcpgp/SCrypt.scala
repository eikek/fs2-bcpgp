package fs2bcpgp

import scodec.bits.ByteVector
import org.bouncycastle.crypto.generators.{SCrypt => SCryptCastle}
import java.lang.Long.toHexString
import java.security.SecureRandom

/** The SCrypt (<a href="http://www.tarsnap.com/scrypt.html">scrypt</a>) from bouncycastle */
object SCrypt {

  def derive(
      pass: ByteVector,
      salt: ByteVector,
      N: Int,
      r: Int,
      p: Int,
      dklen: Int
  ): ByteVector =
    ByteVector.view(SCryptCastle.generate(pass.toArray, salt.toArray, N, r, p, dklen))

  def scrypt(
      pass: String,
      N: Int = 1 << 14,
      r: Int = 8,
      p: Int = 1,
      dklen: Int = 32
  ): String = {
    val random = new SecureRandom()
    val salt = {
      val b = new Array[Byte](16)
      random.nextBytes(b)
      ByteVector.view(b)
    }
    val dk = derive(ByteVector.view(pass.getBytes("UTF-8")), salt, N, r, p, dklen)
    val params = toHexString(log2(N) << 16 | r << 8 | p.toLong)
    s"$$scrypt$$${params}$$${salt.toBase64}$$${dk.toBase64}"
  }

  def check(pass: String, hashed: String): Boolean =
    hashed match {
      case SCrypt(params, salt, derived) =>
        val N = 1 << ((params >> 16) & 0xffff).toInt
        val r = (params >> 8 & 0xff).toInt
        val p = (params & 0xff).toInt
        val len = derived.length.toInt
        val dk1 = derive(ByteVector.view(pass.getBytes("UTF-8")), salt, N, r, p, len)
        derived === dk1
      case _ =>
        false
    }

  private def unapply(hashed: String): Option[(Long, ByteVector, ByteVector)] =
    hashed.split('$').toList match {
      case "" :: "scrypt" :: params :: salt :: derived :: Nil =>
        for {
          p <- ByteVector.fromHex(params).map(_.toLong(true))
          s <- ByteVector.fromBase64(salt)
          d <- ByteVector.fromBase64(derived)
        } yield (p, s, d)
      case _ =>
        None
    }

  def isCrypted(s: String): Boolean =
    s match {
      case SCrypt(_, _, _) => true
      case _               => false
    }

  private def log2(n: Int) =
    31 - Integer.numberOfLeadingZeros(n)
}
