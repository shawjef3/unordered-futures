package me.jeffshaw.futures.unordered

import java.util.concurrent.TimeUnit
import me.jeffshaw.futures.Unordered._
import org.openjdk.jmh.annotations._
import scala.concurrent.ExecutionContext.Implicits.global

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1, jvmArgs = Array("-Xms12g", "-Xmx12g"))
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class UnorderedBenchmarks extends Params with Scheduler {

  @Benchmark
  def toVector(): Unit = {
    Iterator.fill(size) {
      getF(delay)(())
    }.unordered.to[Vector]().await()
  }

  @Benchmark
  def ignore(): Unit = {
    Iterator.fill(size) {
      getF(delay)(())
    }.unordered.ignore().await()
  }

}
