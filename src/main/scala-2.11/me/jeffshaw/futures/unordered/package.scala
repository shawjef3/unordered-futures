package me.jeffshaw.futures

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference, LongAdder}
import java.util.function.{IntUnaryOperator, LongUnaryOperator, UnaryOperator}
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.JavaConverters._
import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.Try

package object unordered {

  def run[A, B](
    futures: Iterator[Future[A]],
    add: A => Unit,
    getResult: () => B,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
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
        }
        p.future
      }

    ConcurrencyLimiter(maybeMaxConcurrency)
      .runAll(accumulations, errorRef, failureAggregator) {
        getResult()
      }
  }

  def ignore[A](
    futures: Iterator[Future[A]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Unit] = {
    val errorRef = new AtomicReference[Throwable]()
    val failureAggregator = FailureAggregator(failAfter, collectCondition)
    val accumulations: Iterator[Future[A]] =
      for (future <- futures) yield {
        future.onComplete {
          (maybeValue: Try[A]) =>
            failureAggregator.add(maybeValue)
        }
        future
      }

    ConcurrencyLimiter(maybeMaxConcurrency)
      .runAll(accumulations, errorRef, failureAggregator)(())
  }

  def fold[A, B](
    futures: Iterator[Future[A]],
    init: B,
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(accum: (B, A) => B
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[B] = {
    val accumulator = new AtomicReference[B](init)
    val add: A => Unit = (value: A) =>
      accumulator.updateAndGet(
        new UnaryOperator[B] {
          override def apply(t: B): B = {
            accum(t, value)
          }
        }
      )
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
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Long] = {
    val accumulator = new AtomicLong(init)
    val add: A => Unit = (value: A) =>
      accumulator.updateAndGet(
        new LongUnaryOperator {
          override def applyAsLong(operand: Long): Long = {
            accum(operand, value)
          }
        }
      )
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
  )(implicit executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Int] = {
    val accumulator = new AtomicInteger(init)
    val add: A => Unit = (value: A) =>
      accumulator.updateAndGet(
        new IntUnaryOperator {
          override def applyAsInt(operand: Int): Int = {
            accum(operand, value)
          }
        }
      )
    val get: () => Int = accumulator.get
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def to[A, Col[_]](
    futures: Iterator[Future[A]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit cbf: CanBuildFrom[Nothing, A, Col[A@uncheckedVariance]],
    executor: ExecutionContext = ExecutionContext.global
  ): WaitMethods[Col[A@uncheckedVariance]] = {
    val accumulator = new ConcurrentLinkedQueue[A]()
    val nullCount = new LongAdder()
    val add: A => Unit = (value: A) => {
      if (value == null) {
        nullCount.increment()
      } else {
        accumulator.add(value)
      }
    }
    val get: () => Col[A] = () => {
      val builder = cbf()
      builder ++= accumulator.asScala
      builder ++= Iterator.fill(nullCount.intValue())(null.asInstanceOf[A])
      builder.result()
    }
    run(futures, add, get, failAfter, collectCondition, maybeMaxConcurrency)
  }

  def sum[A](
    futures: Iterator[Future[A]],
    failAfter: FailAfter = FailAfter.Exact.One,
    collectCondition: CollectCondition = CollectCondition.UpTo.One,
    maybeMaxConcurrency: Option[Int] = None
  )(implicit executor: ExecutionContext = ExecutionContext.global,
    numeric: Numeric[A]
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
  )(implicit executor: ExecutionContext = ExecutionContext.global,
    numeric: Numeric[A]
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
