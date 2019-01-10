package fs2bcpgp

import minitest._

object SCryptSpec extends SimpleTestSuite {

  test("derive and check password") {
    val pw = SCrypt.scrypt("testpw")
    assert(SCrypt.check("testpw", pw))
    assert(! SCrypt.check("testpa", pw))
    assert(SCrypt.isCrypted(pw))
    assert(! SCrypt.isCrypted("testpw"))
  }

  test("max values for r and p") {
    val pw = SCrypt.scrypt("testpw", N = 2, r = 255, p = 255)
    assert(SCrypt.check("testpw", pw))
    assert(SCrypt.isCrypted(pw))
  }

  test("invalid values not throwing") {
    assert(! SCrypt.check("test", "$scrypt$x$x$x"))
  }
}
