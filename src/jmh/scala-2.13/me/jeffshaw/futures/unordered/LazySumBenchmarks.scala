package me.jeffshaw.futures.unordered

import java.util.concurrent.TimeUnit
import me.jeffshaw.futures.Unordered._
import org.openjdk.jmh.annotations._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1, jvmArgs = Array("-Xms12g", "-Xmx12g"))
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class LazySumBenchmarks extends Scheduler {

  @Param(Array("100", "1000"))
  var size: Int = _

  @Param(Array("10"))
  var delay: Long = _

  @Benchmark
  def foldLeft(): Unit = {
    import ExecutionContext.Implicits.global
    val futures: LazyList[Future[Int]] = LazyList.fill(size)(getF(delay)(1))
    val sumF = Future.foldLeft(futures)(0)(_ + _)
    Await.result(sumF, Duration.Inf)
  }

  @Benchmark
  def sequence(): Unit = {
    import ExecutionContext.Implicits.global
    val futures: LazyList[Future[Int]] = LazyList.fill(size)(getF(delay)(1))
    Await.result(Future.sequence(futures), Duration.Inf).sum
  }

  @Benchmark
  def unordered(): Unit = {
    val futures: LazyList[Future[Int]] = LazyList.fill(size)(getF(delay)(1))
    futures.iterator.unordered.sum().await()
  }

}
