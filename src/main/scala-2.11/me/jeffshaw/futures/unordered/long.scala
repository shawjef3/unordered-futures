package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.{LongAccumulator, LongAdder}
import java.util.function.LongBinaryOperator
import scala.concurrent.{ExecutionContext, Future}

object long {
  def fold(
    futures: Iterator[Future[Long]],
    identity: Long,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Long, Long) => Long
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Long] = {
    val accumulator =
      new LongAccumulator(
        new LongBinaryOperator {
          override def applyAsLong(left: Long, right: Long): Long = {
            accum(left, right)
          }
        },
        identity
      )
    val add: Long => Unit = accumulator.accumulate
    val get: () => Long = accumulator.get
    me.jeffshaw.futures.unordered.run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum(
    futures: Iterator[Future[Long]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Long] = {
    val adder = new LongAdder()
    val add: Long => Unit = adder.add
    val get: () => Long = adder.sum
    me.jeffshaw.futures.unordered.run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def product(
    futures: Iterator[Future[Long]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Long] = {
    fold(futures, 1, failAfter, collectCondition, maybeMaxConcurrency)(_ * _)
  }
}
