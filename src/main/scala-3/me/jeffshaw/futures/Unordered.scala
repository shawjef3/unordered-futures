package me.jeffshaw.futures

import me.jeffshaw.futures.unordered.FutureIteratorMethods
import scala.concurrent.Future

trait Unordered {
  implicit class Unordered[A](val futures: Iterator[Future[A]]) {
    def unordered: FutureIteratorMethods[A] = new FutureIteratorMethods[A](futures)
  }
}

object Unordered extends Unordered
