package me.jeffshaw.futures.unordered

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1, jvmArgs = Array("-Xms12g", "-Xmx12g"))
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class FutureBenchmarks extends Params with Scheduler {

  @Benchmark
  def ignore(): Unit = {
    val latch = new java.util.concurrent.CountDownLatch(size)
    for (_ <- 0 until size) {
      getF(delay)(()).onComplete {
        _ => latch.countDown()
      }
    }
    latch.await()
  }

  @Benchmark
  def sum(): Unit = {
    val futures = Future.sequence(Vector.fill(size)(getF(delay)(())))
    Await.result(futures, Duration.Inf)
  }

  @Benchmark
  def toVector(): Unit = {
    val futures = Future.sequence(Vector.fill(size)(getF(delay)(())))
    Await.result(futures, Duration.Inf)
  }

}
