package me.jeffshaw.futures.unordered

import java.util.concurrent.Phaser
import scala.collection.mutable.Buffer
import scala.concurrent.TimeoutException
import scala.concurrent.duration.{Duration, FiniteDuration}

private sealed trait TimeMethods {
  def getRemaining(start: Long): Duration

  def getElapsed(start: Long): Duration = {
    Duration.fromNanos(System.nanoTime() - start)
  }

  def await(start: Long, ps: Buffer[Phaser]): Unit
}

private object TimeMethods {
  def apply(timeout: Duration): TimeMethods = {
    // Mirror the behavior of Promise#tryAwait.
    timeout match {
      case d if d eq Duration.Undefined =>
        throw new IllegalArgumentException("Cannot wait for Undefined duration of time")
      case Duration.Inf =>
        InfiniteTimeout
      case Duration.MinusInf =>
        ZeroTimeout
      case Duration.Zero =>
        ZeroTimeout
      case f: FiniteDuration =>
        if (f <= Duration.Zero) {
          ZeroTimeout
        } else {
          FiniteTimeout(f)
        }
      case _ =>
        throw new IllegalArgumentException("Unknown duration " + timeout)
    }
  }
}

private case class FiniteTimeout(
  timeout: FiniteDuration
) extends TimeMethods {
  override def getRemaining(start: Long): Duration = {
    val remaining = timeout - Duration.fromNanos(System.nanoTime() - start)
    if (remaining < Duration.Zero) {
      throw new TimeoutException("FiniteTimeout.getRemaining")
    } else {
      remaining
    }
  }

  override def await(start: Long, phasers: Buffer[Phaser]): Unit = {
    for (phaser <- phasers) {
      val remaining: Duration = getRemaining(start)
      phaser.awaitAdvanceInterruptibly(0, remaining.length, remaining.unit)
    }
  }
}

private case object ZeroTimeout extends TimeMethods {
  override def getRemaining(start: Long): Duration = {
    Duration.Zero
  }

  override def await(
    start: Long,
    ps: Buffer[Phaser]
  ): Unit = {
    if (ps.exists(!_.isTerminated)) {
      throw new TimeoutException("ZeroTimeout.await")
    }
  }
}

private case object InfiniteTimeout extends TimeMethods {
  override def getRemaining(start: Long): Duration = Duration.Inf

  override final def await(start: Long, phasers: Buffer[Phaser]): Unit = {
    for (phaser <- phasers) {
      phaser.awaitAdvanceInterruptibly(0)
    }
  }
}
