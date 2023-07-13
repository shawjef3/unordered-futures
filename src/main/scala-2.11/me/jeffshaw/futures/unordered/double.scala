package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.{DoubleAccumulator, DoubleAdder}
import java.util.function.DoubleBinaryOperator
import scala.concurrent.{ExecutionContext, Future}

object double {
  def fold(
    futures: Iterator[Future[Double]],
    identity: Double,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Double, Double) => Double
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Double] = {
    val accumulator =
      new DoubleAccumulator(
        new DoubleBinaryOperator {
          override def applyAsDouble(left: Double, right: Double): Double = {
            accum(left, right)
          }
        },
        identity
      )
    val add: Double => Unit = accumulator.accumulate
    val get: () => Double = accumulator.get
    me.jeffshaw.futures.unordered.run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum(
    futures: Iterator[Future[Double]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Double] = {
    val adder = new DoubleAdder()
    val add: Double => Unit = adder.add
    val get: () => Double = adder.sum
    me.jeffshaw.futures.unordered.run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def product(
    futures: Iterator[Future[Double]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Double] = {
    fold(futures, 1, failAfter, collectCondition, maybeMaxConcurrency)(_ * _)
  }
}
