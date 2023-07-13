package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.AtomicBoolean

sealed trait FailAfter {
  private[unordered] def continue(failureAggregator: FailureAggregator): Boolean

  private[unordered] def getExceptionMatcher(): ExceptionMatcher = {
    ExceptionMatcher.None
  }
}

object FailAfter {
  case class Predicate(predicate: Throwable => Boolean) extends FailAfter {
    override private[unordered] def continue(failureAggregator: FailureAggregator): Boolean = {
      !failureAggregator.exceptionMatcher.getMatched()
    }

    private[unordered] override def getExceptionMatcher(): ExceptionMatcher = {
      new ExceptionMatcher.Predicate(predicate)
    }
  }

  abstract case class Exact private (n: Int) extends FailAfter {
    private[unordered] override def continue(failureAggregator: FailureAggregator): Boolean = {
      failureAggregator.getFailureCount() < n
    }
  }

  object Exact {
    def apply(n: Int): FailAfter = {
      if (n < 1) {
        throw new IllegalArgumentException("n must be > 0")
      }

      if (n == 1) {
        One
      } else {
        new Exact(n) { }
      }
    }

    val One: Exact = new Exact(1) { }
  }

  case class Proportion(p: Double) extends FailAfter {
    private[unordered] override def continue(failureAggregator: FailureAggregator): Boolean = {
      failureAggregator.getFailurePercent() < p
    }
  }

  case object Never extends FailAfter {
    private[unordered] override def continue(failureAggregator: FailureAggregator): Boolean = {
      true
    }
  }
}
