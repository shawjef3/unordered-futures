package me.jeffshaw.futures.unordered

import java.util.AbstractQueue
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, LinkedBlockingQueue, ThreadLocalRandom}
import java.util.concurrent.atomic.LongAdder
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

private sealed abstract class FailureAggregator(
  val failAfter: FailAfter
) {
  private val successCounter = new LongAdder()
  private val failureCounter = new LongAdder()
  val exceptionMatcher: ExceptionMatcher = failAfter.getExceptionMatcher()

  protected def add(t: Throwable): Boolean

  def add[A](t: Try[A]): Boolean = {
    t match {
      case Failure(exception) =>
        exceptionMatcher.check(exception)
        failureCounter.increment()
        add(exception)
      case _ =>
        successCounter.increment()
        true
    }
  }

  def getFailureCount(): Long = {
    failureCounter.sum()
  }

  def getSuccessCount(): Long = {
    successCounter.sum()
  }

  def getCount(): Long = {
    getFailureCount() + getSuccessCount()
  }

  def getSuccessPercent(): Double = {
    val failureCount: Long = getFailureCount()
    val successCount: Long = getSuccessCount()
    val total: Double = successCount.toDouble + failureCount.toDouble
    successCount.toDouble / total
  }

  def getFailurePercent(): Double = {
    1D - getSuccessPercent()
  }

  def getExceptions(): Iterable[Throwable]

  def continue(): Boolean = {
    failAfter.continue(this)
  }

  def isFailure(): Boolean = {
    !continue()
  }

  def getResult[A](
    maybeError: Throwable,
    getResult: => A
  ): A = {
    if (maybeError != null) {
      for (suppressed <- getExceptions()) {
        maybeError.addSuppressed(suppressed)
      }
      throw maybeError
    } else {
      if (isFailure()) {
        val exceptions = getExceptions().iterator
        if (exceptions.hasNext) {
          val exception = exceptions.next()
          for (suppressed <- exceptions) {
            exception.addSuppressed(suppressed)
          }
          throw exception
        } else {
          throw new NoSuchElementException("There were exceptions but none were collected.")
        }
      } else {
        try {
          getResult
        } catch {
          case NonFatal(exception) =>
            val exceptions = getExceptions()
            for (suppressed <- exceptions) {
              exception.addSuppressed(suppressed)
            }
            throw exception
        }
      }
    }
  }
}

private object FailureAggregator {
  def apply(
    failAfter: FailAfter,
    collectCondition: CollectCondition
  ): FailureAggregator = {
    collectCondition match {
      case CollectCondition.Custom(predicate, maybeMax) =>
        new FailureAggregator(failAfter) {
          private val q: AbstractQueue[Throwable] =
            maybeMax match {
              case Some(max) =>
                new LinkedBlockingQueue(max)
              case None =>
                new ConcurrentLinkedQueue()
            }

          override protected def add(t: Throwable): Boolean = {
            predicate(t) && q.offer(t)
          }

          override def getExceptions(): Iterable[Throwable] = q.asScala
        }

      case CollectCondition.Probability(p, maybeMax) =>
        new FailureAggregator(failAfter) {
          private val q: AbstractQueue[Throwable] =
            maybeMax match {
              case Some(max) =>
                new LinkedBlockingQueue(max)
              case None =>
                new ConcurrentLinkedQueue()
            }

          override protected def add(t: Throwable): Boolean = {
            ThreadLocalRandom.current().nextDouble() < p && q.offer(t)
          }

          override def getExceptions(): Iterable[Throwable] = {
            q.asScala
          }
        }

      case CollectCondition.Never =>
        new FailureAggregator(failAfter) {
          override protected def add(t: Throwable): Boolean = false

          override def getExceptions(): Iterable[Throwable] = Iterable.empty
        }

      case CollectCondition.Always =>
        new FailureAggregator(failAfter) {
          private val q = new ConcurrentLinkedQueue[Throwable]()

          override protected def add(t: Throwable): Boolean = {
            q.offer(t)
          }

          override def getExceptions(): Iterable[Throwable] = {
            q.asScala
          }
        }

      case CollectCondition.OneEach(maybeMax) =>
        new FailureAggregator(failAfter) {
          private val failures = new ConcurrentHashMap[Class[_], Throwable]()

          private def put(t: Throwable): Boolean = {
            failures.putIfAbsent(t.getClass, t) == null
          }

          private val addImpl: Throwable => Boolean =
            maybeMax match {
              case Some(max) =>
                (t: Throwable) => {
                  if (failures.size() < max) {
                    put(t)
                  } else {
                    false
                  }
                }
              case None =>
                put
            }

          override protected def add(t: Throwable): Boolean = {
            addImpl(t)
          }

          override def getExceptions(): Iterable[Throwable] = {
            failures.values().asScala
          }
        }

      case CollectCondition.UpTo(n) =>
        new FailureAggregator(failAfter) {
          private val q = new LinkedBlockingQueue[Throwable](n)

          override protected def add(t: Throwable): Boolean = {
            q.offer(t)
          }

          override def getExceptions(): Iterable[Throwable] = {
            q.asScala
          }
        }
    }
  }
}
