package me.jeffshaw.futures.unordered

import java.util.Timer
import java.util.concurrent.{Phaser, TimeoutException}
import java.util.concurrent.atomic.AtomicReference
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable.Buffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class WaitMethodsSpec extends AnyFunSuite {

  test("future returns immediately") {
    val phasers: Buffer[Phaser] = Buffer(new Phaser(1))
    val errorRef: AtomicReference[Throwable] = new AtomicReference()
    val expected = 3
    val w = new WaitMethods.Delayed(phasers, errorRef, FailureAggregator(FailAfter.Exact.One, CollectCondition.UpTo.One), expected)
    val f: Future[Int] = w.future()
    assert(!f.isCompleted)
    phasers.head.arriveAndDeregister()
    val actual: Int = w.await(Duration.Zero)
    assertResult(expected)(actual)
    assertResult(expected)(Await.result(f, Duration.Zero))
  }

  test("throws Timeout exception") {
    val phasers: Buffer[Phaser] = Buffer(new Phaser(1))
    val errorRef: AtomicReference[Throwable] = new AtomicReference()
    val w = new WaitMethods.Delayed(phasers, errorRef, FailureAggregator(FailAfter.Exact.One, CollectCondition.UpTo.One), ())
    assertThrows[TimeoutException](w.await(Duration("0s")))
  }

  test("other timeouts apply") {
    val phasers: Buffer[Phaser] = Buffer(new Phaser(1))
    val errorRef: AtomicReference[Throwable] = new AtomicReference()
    val expected = 3
    val w = new WaitMethods.Delayed(phasers, errorRef, FailureAggregator(FailAfter.Exact.One, CollectCondition.UpTo.One), expected)
    val t = new Timer()
    t.schedule(new java.util.TimerTask {
      override def run(): Unit = {
        phasers.head.arriveAndDeregister()
      }
    }, 1000)
    assertThrows[TimeoutException](w.await(Duration.Zero))
    assertResult(expected)(w.await())
  }

  test("iterator") {
    val phasers: Buffer[Phaser] = Buffer(new Phaser(1))
    val errorRef: AtomicReference[Throwable] = new AtomicReference()
    val expected = 3
    val w = new WaitMethods.Delayed(phasers, errorRef, FailureAggregator(FailAfter.Exact.One, CollectCondition.UpTo.One), expected)
    phasers.head.arriveAndDeregister()
    assert(Iterator(expected).sameElements(w.iterator))
  }

}
