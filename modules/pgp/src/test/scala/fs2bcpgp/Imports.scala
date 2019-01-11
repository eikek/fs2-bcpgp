package fs2bcpgp

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import cats.effect._

trait Imports {

  def blockingEC: ExecutionContext

  implicit def contextShift: ContextShift[IO]

}

object Imports {

  trait Defaults extends Imports {

    def blockingEC = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

    implicit def contextShift: ContextShift[IO] =
      IO.contextShift(ExecutionContext.global)
  }
}
