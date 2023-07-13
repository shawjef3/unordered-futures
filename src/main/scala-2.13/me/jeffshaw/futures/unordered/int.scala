package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.{LongAccumulator, LongAdder}
import scala.concurrent.Future

object int {
  def fold(
    futures: Iterator[Future[Int]],
    identity: Int,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Int, Int) => Int
  ): WaitMethods[Int] = {
    val accumulator = 
      new LongAccumulator((left: Long, right: Long) => accum(left.toInt, right.toInt).toLong, identity.toLong)
    val add: Int => Unit = (value: Int) => accumulator.accumulate(value.toLong)
    val get: () => Int = accumulator.intValue
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum(
    futures: Iterator[Future[Int]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[Int] = {
    val adder = new LongAdder()
    val add: Int => Unit = (value: Int) => adder.add(value.toLong)
    val get: () => Int = () => adder.sum.toInt
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def product(
    futures: Iterator[Future[Int]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[Int] = {
    fold(futures, 1, failAfter, collectCondition, maybeMaxConcurrency)(_ * _)
  }
}
