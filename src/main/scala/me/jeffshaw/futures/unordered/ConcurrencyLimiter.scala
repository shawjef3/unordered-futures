package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{Phaser, Semaphore}
import scala.collection.mutable.{Buffer, ListBuffer}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

private sealed trait ConcurrencyLimiter {
  def acquire(): Unit

  def release(): Unit

  private def run[A](
    phasers: Buffer[Phaser],
    fs: Iterator[Future[A]]
  )(implicit executor: ExecutionContext
  ): Future[A] = {
    acquire()
    val phaser = ConcurrencyLimiter.getPhaser(phasers)
    phaser.register()
    val started: Future[A] = fs.next()

    started.onComplete { (result: Try[A]) =>
      release()
      phaser.arriveAndDeregister()
    }

    started
  }

  def runAll[A, B](
    fs: Iterator[Future[A]],
    errorRef: AtomicReference[Throwable],
    failureAggregator: FailureAggregator
  )(getResult: => B
  )(implicit executor: ExecutionContext
  ): WaitMethods[B] = {
    if (fs.isEmpty) {
      new WaitMethods.Immediate(getResult)
    } else {
      /*
      Using a Phaser lets us wait for all the Futures to finish without having to form a collection
      of Futures.
       */
      val phasers: Buffer[Phaser] = ListBuffer.empty
      /*
      A `for` loop here would force each future to begin executing before
      we acquire permits.
       */
      while (fs.hasNext && errorRef.get() == null && failureAggregator.continue()) {
        run(phasers, fs)
      }
      for (phaser <- phasers) {
        phaser.arriveAndDeregister()
      }
      new WaitMethods.Delayed(phasers, errorRef, failureAggregator, getResult)
    }
  }
}

private object ConcurrencyLimiter {
  def apply(
    maybeMaxConcurrency: Option[Int]
  ): ConcurrencyLimiter = {
    maybeMaxConcurrency match {
      case Some(maxConcurrency) =>
        Limited(maxConcurrency)
      case None =>
        Unlimited
    }
  }

  def getPhaser(phasers: Buffer[Phaser]): Phaser = {
    phasers
      .find(_.getRegisteredParties < 1000)
      .getOrElse {
        val phaser = new Phaser(1)
        phasers += phaser
        phaser
      }
  }

  private case class Limited(
    maxConcurrency: Int
  ) extends ConcurrencyLimiter {
    if (maxConcurrency < 1) {
      throw new IllegalArgumentException("maxConcurrency must be >= 1")
    }

    private val semaphore = new Semaphore(maxConcurrency)

    override def acquire(): Unit = {
      semaphore.acquire()
    }

    override def release(): Unit = {
      semaphore.release()
    }
  }

  private case object Unlimited extends ConcurrencyLimiter {
    override def acquire(): Unit = ()

    override def release(): Unit = ()
  }
}
