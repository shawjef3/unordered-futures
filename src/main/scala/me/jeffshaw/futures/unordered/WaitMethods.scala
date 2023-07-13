package me.jeffshaw.futures.unordered

import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable.Buffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

sealed trait WaitMethods[A] extends Iterable[A] {
  def await(timeout: Duration = Duration.Inf): A
  def future()(implicit ec: ExecutionContext = ExecutionContext.global): Future[A]

  def iterator(timeout: Duration): Iterator[A] = {
    Iterator(await(timeout))
  }

  override def iterator: Iterator[A] = {
    iterator(Duration.Inf)
  }
}

private object WaitMethods {
  private[futures] class Delayed[A](
    phasers: Buffer[Phaser],
    errorRef: AtomicReference[Throwable],
    failureAggregator: FailureAggregator,
    getResult: => A
  ) extends WaitMethods[A] {
    override def await(timeout: Duration = Duration.Inf): A = {
      val start = System.nanoTime()
      val timeMethods = TimeMethods(timeout)
      timeMethods.await(start, phasers)
      failureAggregator.getResult(errorRef.get, getResult)
    }

    override def future()(implicit ec: ExecutionContext = ExecutionContext.global): Future[A] = {
      for {
        _ <- Future(InfiniteTimeout.await(0, phasers))
      } yield failureAggregator.getResult(errorRef.get, getResult)
    }
  }

  private[futures] class Immediate[A](getResult: => A) extends WaitMethods[A] {
    lazy val result: Try[A] = Try(getResult)
    lazy val f: Future[A] = Future.fromTry(result)
    override def await(timeout: Duration): A = {
      result.get
    }

    override def future()(implicit ec: ExecutionContext = ExecutionContext.global): Future[A] = {
      f
    }
  }

  private[futures] class Mapped[A, B](w: WaitMethods[A], f: A => B) extends WaitMethods[B] {
    override def await(timeout: Duration = Duration.Inf): B = {
      f(w.await(timeout))
    }

    override def future()(implicit ec: ExecutionContext = ExecutionContext.global): Future[B] = {
      w.future().map(f)
    }
  }

  private[futures] class FlatMapped[A, B](w: WaitMethods[A], f: A => WaitMethods[B]) extends WaitMethods[B] {
    override def await(timeout: Duration = Duration.Inf): B = {
      val start = System.nanoTime()
      val wResult: A = w.await(timeout)
      val remaining = timeout - Duration.fromNanos(System.nanoTime() - start)
      f(wResult).await(remaining)
    }

    override def future()(implicit ec: ExecutionContext = ExecutionContext.global): Future[B] = {
      for {
        wResult <- w.future()
        fResult <- f(wResult).future()
      } yield fResult
    }
  }
}
