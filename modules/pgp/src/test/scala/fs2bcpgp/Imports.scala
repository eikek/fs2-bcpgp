package fs2bcpgp

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import cats.effect._

trait Imports {

  def blocker: Blocker

  implicit def contextShift: ContextShift[IO]

}

object Imports {

  trait Defaults extends Imports {

    val blockingEC = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)
    val blocker = Blocker.liftExecutionContext(blockingEC)

    implicit def contextShift: ContextShift[IO] =
      IO.contextShift(ExecutionContext.global)
  }
}
