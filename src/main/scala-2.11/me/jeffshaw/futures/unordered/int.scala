package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.{LongAccumulator, LongAdder}
import java.util.function.LongBinaryOperator
import scala.concurrent.{ExecutionContext, Future}

object int {
  def fold(
    futures: Iterator[Future[Int]],
    identity: Int,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Int, Int) => Int
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Int] = {
    val accumulator =
      new LongAccumulator(
        new LongBinaryOperator {
          override def applyAsLong(left: Long, right: Long): Long = {
            accum(left.toInt, right.toInt).toLong
          }
        },
        identity
      )
    val add: Int => Unit = (value: Int) => accumulator.accumulate(value.toLong)
    val get: () => Int = accumulator.intValue
    me.jeffshaw.futures.unordered.run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum(
    futures: Iterator[Future[Int]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Int] = {
    val adder = new LongAdder()
    val add: Int => Unit = (value: Int) => adder.add(value.toLong)
    val get: () => Int = () => adder.sum.toInt
    me.jeffshaw.futures.unordered.run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def product(
    futures: Iterator[Future[Int]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Int] = {
    fold(futures, 1, failAfter, collectCondition, maybeMaxConcurrency)(_ * _)
  }
}
