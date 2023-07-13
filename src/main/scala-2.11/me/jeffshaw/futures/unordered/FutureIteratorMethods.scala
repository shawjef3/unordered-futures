package me.jeffshaw.futures.unordered

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ExecutionContext, Future}

class FutureIteratorMethods[A](val futures: Iterator[Future[A]]) extends AnyVal {
  def run[B](
    add: A => Unit,
    get: () => B,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[B] = {
    me.jeffshaw.futures.unordered.run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  /**
   * Block until all futures are completed, ignoring results.
   *
   * @param maybeMaxConcurrency If defined, limits the number of concurrent futures.
   */
  def ignore(
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Unit] = {
    me.jeffshaw.futures.unordered.ignore(futures, failAfter, collectCondition, maybeMaxConcurrency)
  }

  /**
   * This is like [[Future.fold()]], but faster because it doesn't create a collection from all
   * the futures and then wait for them all to complete before folding.
   *
   * This is also like [[Future.foldLeft()]], but faster because it doesn't execute the futures
   * one at a time.
   *
   * It sacrifices ordering.
   *
   * @param maybeMaxConcurrency If defined, limits the number of concurrent futures.
   * @param executor            is used for the accumulation function.
   */
  def fold[B](
    init: B,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (B, A) => B
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[B] = {
    me.jeffshaw.futures.unordered.fold(futures, init, failAfter, collectCondition, maybeMaxConcurrency)(accum)
  }

  def foldLong(
    init: Long,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Long, Long) => Long
  )(implicit executor: ExecutionContext = ExecutionContext.global,
    ev: A =:= Long
  ): WaitMethods[Long] = {
    me.jeffshaw.futures.unordered.long.fold(futures.asInstanceOf[Iterator[Future[Long]]], init, failAfter, collectCondition, maybeMaxConcurrency)(accum)
  }

  def foldInt(
    init: Int,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Int, Int) => Int
  )(implicit executor: ExecutionContext = ExecutionContext.global,
    ev: A =:= Int
  ): WaitMethods[Int] = {
    me.jeffshaw.futures.unordered.int.fold(futures.asInstanceOf[Iterator[Future[Int]]], init, failAfter, collectCondition, maybeMaxConcurrency)(accum)
  }

  def foldDouble(
    init: Double,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Double, Double) => Double
  )(implicit executor: ExecutionContext = ExecutionContext.global,
    ev: A =:= Double
  ): WaitMethods[Double] = {
    me.jeffshaw.futures.unordered.double.fold(futures.asInstanceOf[Iterator[Future[Double]]], init, failAfter, collectCondition, maybeMaxConcurrency)(accum)
  }

  def to[Col[_]](
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit cbf: CanBuildFrom[Nothing, A, Col[A@uncheckedVariance]],
    executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Col[A@uncheckedVariance]] = {
    me.jeffshaw.futures.unordered.to(futures, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum(
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global,
    numeric: Numeric[A]
  ): WaitMethods[A] = {
    me.jeffshaw.futures.unordered.sum(futures, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def product(
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global,
    numeric: Numeric[A]
  ): WaitMethods[A] = {
    me.jeffshaw.futures.unordered.product(futures, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def foldToLong(
    init: Long,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Long, A) => Long
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Long] = {
    me.jeffshaw.futures.unordered.foldToLong(futures, init, failAfter, collectCondition, maybeMaxConcurrency)(accum)
  }

  def foldToInt(
    init: Int,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Int, A) => Int
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Int] = {
    me.jeffshaw.futures.unordered.foldToInt(futures, init, failAfter, collectCondition, maybeMaxConcurrency)(accum)
  }
}
