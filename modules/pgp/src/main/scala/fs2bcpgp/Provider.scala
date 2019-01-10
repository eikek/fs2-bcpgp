package fs2bcpgp

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

object Provider {

  Security.addProvider(new BouncyCastleProvider)

  val name = "BC"

}
