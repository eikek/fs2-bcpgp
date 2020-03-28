package fs2bcpgp

import java.util._
import scala.jdk.CollectionConverters._

object ScalaCompat {

  implicit final class IteratorOps[A](iter: Iterator[A]) {
    def toScala =
      iter.asScala
  }

  implicit final class ListOps[A](list: List[A]) {
    def toScala =
      list.asScala
  }

  implicit final class CollectionOps[A](list: Collection[A]) {
    def toScala =
      list.asScala
  }

  implicit final class IterableOps[A](list: java.lang.Iterable[A]) {
    def toScala =
      list.asScala
  }

}
