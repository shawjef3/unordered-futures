package me.jeffshaw.futures.unordered

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import me.jeffshaw.futures.Unordered._

class FutureIteratorMethodsSpec extends AnyFunSuite with TableDrivenPropertyChecks {

  class CustomThrowable extends Throwable
  val isCustomThrowable: Throwable => Boolean = _.isInstanceOf[CustomThrowable]

  test("foldUnordered empty") {
    val fs: Iterator[Future[Int]] = Iterator.empty

    val expected: Int = 0
    val actual: Int = fs.unordered.sum().await()
    assertResult(expected)(actual)
  }

  test("foldUnordered delayed") {
    def fs(): Iterator[Future[Int]] =
      Iterator.tabulate(100) { i =>
        Future {
          Thread.sleep(5)
          i
        }
      }
    // https://en.wikipedia.org/wiki/1_%2B_2_%2B_3_%2B_4_%2B_%E2%8B%AF
    val expected: Int = (99D * 100D / 2D).toInt
    val actual: Int = fs().unordered.sum().await()
    assertResult(expected)(actual)
  }

  test("wait one from multiple futures") {
    val complete = new CountDownLatch(1)
    val increments = new AtomicInteger()
    val waitMethods: WaitMethods[Int] =
      Iterator(
        Future {
          complete.await()
          increments.incrementAndGet()
          1
        }
      ).unordered.sum()
    val waiters: Vector[Future[Int]] = Vector.fill(100)(waitMethods.future())

    complete.countDown()

    for (waiter <- waiters) {
      assertResult(1)(Await.result(waiter, Duration.Inf))
    }

    assertResult(1)(increments.get())
  }

  test("wait multiple from multiple futures") {
    val complete = new CountDownLatch(1)
    val increments = new AtomicInteger()
    val waitMethods: WaitMethods[Int] =
      Iterator.fill(100) {
        Future {
          complete.await()
          increments.incrementAndGet()
          1
        }
      }.unordered.sum()
    val waiters: Vector[Future[Int]] = Vector.fill(100)(waitMethods.future())

    complete.countDown()

    for (waiter <- waiters) {
      assertResult(100)(Await.result(waiter, Duration.Inf))
    }

    assertResult(100)(increments.get())
  }

  test("Iterator[Future[Int]] sum") {
    assertCompiles("(null: Iterator[Future[Int]]).unordered.sum()")
  }

  test("Predicate with collection") {
    val fs: Iterator[Future[Int]] = Iterator(
      Future.successful(3),
      Future.failed(new CustomThrowable)
    )

    assertThrows[CustomThrowable](fs.unordered.sum(failAfter = FailAfter.Predicate(isCustomThrowable), collectCondition = CollectCondition.Custom(isCustomThrowable)).await())
  }

  test("Predicate without collection") {
    val fs: Iterator[Future[Int]] = Iterator(
      Future.successful(3),
      Future.failed(new CustomThrowable)
    )

    assertThrows[NoSuchElementException](fs.unordered.sum(failAfter = FailAfter.Predicate(isCustomThrowable), collectCondition = CollectCondition.Never).await())
  }

  test("iterable") {
    val wait: WaitMethods[List[Int]] =
      Iterator(1, 2, 3).map(Future.successful).unordered.fold(List.empty[Int]) {
        case (accum, i) =>
          i::accum
      }

    val actual: Iterable[Int] =
      for {
        list <- wait
        element <- list
      } yield element

    assertResult(Set(1,2,3))(actual.toSet)
    assertResult(3)(actual.size)
  }

}
