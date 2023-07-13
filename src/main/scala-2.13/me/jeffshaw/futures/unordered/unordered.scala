package me.jeffshaw.futures

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference, DoubleAccumulator, DoubleAdder, LongAccumulator, LongAdder}
import java.util.function.{DoubleBinaryOperator, IntUnaryOperator, LongBinaryOperator, LongUnaryOperator, UnaryOperator}
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.{BuildFrom, Factory, mutable}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

package object unordered {
  def run[A, B](
    futures: Iterator[Future[A]],
    add: A => Unit,
    get: () => B,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[B] = {
    val errorRef = new AtomicReference[Throwable]()
    val failureAggregator = FailureAggregator(failAfter, collectCondition)
    val accumulations: Iterator[Future[Unit]] =
      for (future <- futures) yield {
        val p = Promise[Unit]()
        future.onComplete {
          (maybeValue: Try[A]) =>
            failureAggregator.add(maybeValue)
            for (value <- maybeValue) {
              try {
                add(value)
              } catch {
                case NonFatal(e) =>
                  errorRef.compareAndSet(null, e)
              }
            }
            p.success(())
        }(ExecutionContext.parasitic)
        p.future
      }

    ConcurrencyLimiter(maybeMaxConcurrency)
      .runAll(accumulations, errorRef, failureAggregator) {
        get()
      }(ExecutionContext.parasitic)
  }

  def ignore[A](
    futures: Iterator[Future[A]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[Unit] = {
    val errorRef = new AtomicReference[Throwable]()
    val failureAggregator = FailureAggregator(failAfter, collectCondition)
    val accumulations: Iterator[Future[A]] =
      for (future <- futures) yield {
        future.onComplete {
          (maybeValue: Try[A]) =>
            failureAggregator.add(maybeValue)
        }(ExecutionContext.parasitic)
        future
      }

    ConcurrencyLimiter(maybeMaxConcurrency)
      .runAll(accumulations, errorRef, failureAggregator)(())(ExecutionContext.parasitic)
  }

  def fold[A, B](
    futures: Iterator[Future[A]],
    init: B,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (B, A) => B
  ): WaitMethods[B] = {
    val accumulator = new AtomicReference[B](init)
    val add: A => Unit = (value: A) => {
      accumulator.updateAndGet((t: B) => accum(t, value))
    }
    val get: () => B = accumulator.get
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def foldToLong[A](
    futures: Iterator[Future[A]],
    init: Long,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Long, A) => Long
  ): WaitMethods[Long] = {
    val accumulator = new AtomicLong(init)
    val add: A => Unit = (value: A) => {
      accumulator.updateAndGet((operand: Long) => accum(operand, value))
    }
    val get: () => Long = accumulator.get
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def foldToInt[A](
    futures: Iterator[Future[A]],
    init: Int,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (Int, A) => Int
  ): WaitMethods[Int] = {
    val accumulator = new AtomicInteger(init)
    val add: A => Unit = (value: A) => accumulator.updateAndGet((operand: Int) => accum(operand, value))
    val get: () => Int = accumulator.get
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def to[A, C](
    futures: Iterator[Future[A]],
    factory: Factory[A, C],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  ): WaitMethods[C] = {
    val accumulator = new ConcurrentLinkedQueue[A]()
    val nullCount = new LongAdder()
    val add: A => Unit = (value: A) => {
      if (value == null) {
        nullCount.increment()
      } else {
        accumulator.add(value)
      }
    }

    val get: () => C = () => {
      (accumulator.iterator.asScala ++ Iterator.fill(nullCount.intValue())(null.asInstanceOf[A])).to(factory)
    }
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum[A](
    futures: Iterator[Future[A]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit numeric: Numeric[A]
  ): WaitMethods[A] = {
    if (numeric eq implicitly[Numeric[Int]]) {
      int.sum(futures.asInstanceOf[Iterator[Future[Int]]], failAfter, collectCondition, maybeMaxConcurrency)
        .asInstanceOf[WaitMethods[A]]
    } else if (numeric eq implicitly[Numeric[Long]]) {
      long.sum(futures.asInstanceOf[Iterator[Future[Long]]], failAfter, collectCondition, maybeMaxConcurrency)
        .asInstanceOf[WaitMethods[A]]
    } else if (numeric eq implicitly[Numeric[Double]]) {
      double.sum(futures.asInstanceOf[Iterator[Future[Double]]], failAfter, collectCondition, maybeMaxConcurrency)
        .asInstanceOf[WaitMethods[A]]
    } else {
      fold(futures, numeric.zero, failAfter, collectCondition, maybeMaxConcurrency)(numeric.plus)
    }
  }

  def product[A](
    futures: Iterator[Future[A]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit numeric: Numeric[A]
  ): WaitMethods[A] = {
    if (numeric eq implicitly[Numeric[Int]]) {
      int.product(futures.asInstanceOf[Iterator[Future[Int]]], failAfter, collectCondition, maybeMaxConcurrency)
        .asInstanceOf[WaitMethods[A]]
    } else if (numeric eq implicitly[Numeric[Long]]) {
      long.product(futures.asInstanceOf[Iterator[Future[Long]]], failAfter, collectCondition, maybeMaxConcurrency)
        .asInstanceOf[WaitMethods[A]]
    } else if (numeric eq implicitly[Numeric[Double]]) {
      double.product(futures.asInstanceOf[Iterator[Future[Double]]], failAfter, collectCondition, maybeMaxConcurrency)
        .asInstanceOf[WaitMethods[A]]
    } else {
      fold(futures, numeric.one, failAfter, collectCondition, maybeMaxConcurrency)(numeric.times)
    }
  }
}
