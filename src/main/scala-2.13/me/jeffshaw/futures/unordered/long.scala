package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.{LongAccumulator, LongAdder}
import scala.concurrent.Future

object long {
  def fold(
    futures: Iterator[Future[Long]],
    identity: Long,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Long, Long) => Long
  ): WaitMethods[Long] = {
    val accumulator = new LongAccumulator(accum(_, _), identity)
    val add: Long => Unit = accumulator.accumulate
    val get: () => Long = accumulator.get
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum(
    futures: Iterator[Future[Long]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[Long] = {
    val adder = new LongAdder()
    val add: Long => Unit = adder.add
    val get: () => Long = adder.sum
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def product(
    futures: Iterator[Future[Long]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[Long] = {
    fold(futures, 1, failAfter, collectCondition, maybeMaxConcurrency)(_ * _)
  }
}
