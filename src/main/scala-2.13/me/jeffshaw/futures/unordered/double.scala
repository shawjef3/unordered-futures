package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.{DoubleAccumulator, DoubleAdder}
import scala.concurrent.Future

object double {
  def fold(
    futures: Iterator[Future[Double]],
    identity: Double,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Double, Double) => Double
  ): WaitMethods[Double] = {
    val accumulator = new DoubleAccumulator(accum(_, _), identity)
    val add: Double => Unit = accumulator.accumulate
    val get: () => Double = accumulator.get
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum(
    futures: Iterator[Future[Double]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[Double] = {
    val adder = new DoubleAdder()
    val add: Double => Unit = adder.add
    val get: () => Double = adder.sum
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def product(
    futures: Iterator[Future[Double]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[Double] = {
    fold(futures, 1, failAfter, collectCondition, maybeMaxConcurrency)(_ * _)
  }
}
