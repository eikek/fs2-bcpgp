package fs2bcpgp

import java.time.{Duration, Instant}
import minitest._
import cats.effect.IO

import fs2bcpgp.SymmetricAlgo._

object KeystoreSpec extends SimpleTestSuite {

  lazy val generated = Keystore.generate[IO]("eike", "test".toCharArray).unsafeRunSync

  test ("Generate key pair") {
    val ks = Keystore.generate[IO]("eike", "test".toCharArray).unsafeRunSync
    val pubkey = ks.public.findByUserID[IO]("eike").unsafeRunSync.get
    assertEquals(pubkey.userIDs, List("eike"))
    assertEquals(pubkey.strength, 4096)
    assertEquals(pubkey.version, 4)
    assertEquals(pubkey.validity, Duration.ZERO)
    assert(pubkey.isEncryptionKey)
    assert(pubkey.isMasterKey)
    assertEquals(pubkey.preferedAlgorithms, List(Twofish, AES256, AES192, AES128))

    val seckey = ks.secret.findByUserID[IO]("eike").unsafeRunSync.get
    assertEquals(seckey.pubKey.id, pubkey.id)
    assertEquals(seckey.userIDs, List("eike"))

    val pkey = seckey.extractPrivateKey[IO]("test".toCharArray).unsafeRunSync
    assertEquals(pkey.keyId, seckey.keyId)

    intercept[Exception] {
      seckey.extractPrivateKey[IO]("test12".toCharArray).unsafeRunSync
    }
  }

  test ("Read keystore from inputstream") {
    // generated via `gpg --quick-gen-key`
    val ks = Keystore.fromInputStream(IO(getClass.getResource("/all.kr").openStream)).unsafeRunSync
    val pubkey = ks.public.findByUserID[IO]("test").unsafeRunSync.get
    assertEquals(pubkey.strength, 2048)
    assertEquals(pubkey.version, 4)
    assertEquals(pubkey.created, Instant.parse("2017-12-01T21:43:05Z"))
    assertEquals(pubkey.validity, Duration.ofDays(730))
    assert(pubkey.isEncryptionKey)
    assert(pubkey.isMasterKey)
    assertEquals(pubkey.preferedAlgorithms, List(AES256, AES192, AES128, DES3))

    val seckey = ks.secret.findByUserID[IO]("test").unsafeRunSync.get
    assertEquals(seckey.pubKey.id, pubkey.id)
    assertEquals(seckey.userIDs, List("test"))

    val pkey = seckey.extractPrivateKey[IO]("test".toCharArray).unsafeRunSync
    assertEquals(pkey.keyId, seckey.keyId)
  }

  test ("Concatenate keystore") {
    val ks0 = generated
    val ks1 = Keystore.fromInputStream(IO(getClass.getResource("/all.kr").openStream)).unsafeRunSync
    val ks = ks0 ++ ks1

    val pubkey0 = ks.public.findByUserID[IO]("eike").unsafeRunSync.get
    val pubkey1 = ks.public.findByUserID[IO]("test").unsafeRunSync.get
    assertEquals(pubkey0.strength, 4096)
    assertEquals(pubkey1.strength, 2048)
  }

  test ("serialise / deserialise") {
    val ks1 = Keystore.fromInputStream(IO(getClass.getResource("/all.kr").openStream)).unsafeRunSync
    val ks1str = ks1.armored[IO].flatMap(Keystore.fromArmoredString[IO]).unsafeRunSync
    assertEquals(ks1, ks1str)

    val ks0 = ks1 ++ generated
    val ks0str = ks0.armored[IO].flatMap(Keystore.fromArmoredString[IO]).unsafeRunSync
    assertEquals(ks0, ks0str)
  }

  test ("checkPassword") {
    val ks1 = Keystore.fromInputStream(IO(getClass.getResource("/all.kr").openStream)).unsafeRunSync
    ks1.checkPassword[IO](_ => "test".toCharArray).map({ list1 =>
      assertEquals(list1.size, 1)
      assertEquals(list1(0), -2031006086266986872L -> true)
    }).unsafeRunSync
    ks1.checkPassword[IO](_ => "abc".toCharArray).map({ list1 =>
      assertEquals(list1.size, 1)
      assertEquals(list1(0), -2031006086266986872L -> false)
    }).unsafeRunSync


    val genKeyId = generated.public.all[IO].map(_.head.keyId)

    (generated ++ ks1).checkPassword[IO](Map(-2031006086266986872L -> "test".toCharArray).withDefaultValue("abc".toCharArray)).flatMap({ list =>
      assertEquals(list.size, 2)
      genKeyId.map { kid =>
        assertEquals(list.toMap.apply(-2031006086266986872L), true)
        assertEquals(list.toMap.apply(kid), false)
      }
    }).unsafeRunSync
  }
}
