package me.jeffshaw.futures.unordered

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ScheduledExecutorService, ThreadFactory, TimeUnit}
import org.openjdk.jmh.annotations.{Setup, TearDown}
import scala.concurrent.{Future, Promise}

trait Scheduler {

  private val executorCounter = new AtomicInteger()
  private var scheduler: ScheduledExecutorService = _

  @Setup
  def open(): Unit = {
    scheduler =
      Executors.newScheduledThreadPool(
        Runtime.getRuntime.availableProcessors(),
        new ThreadFactory {
          val d = Executors.defaultThreadFactory()
          val i = new AtomicInteger()

          def getName(): String = {
            "benchmark-scheduler-" + executorCounter.getAndIncrement() + "-" + i.getAndIncrement()
          }

          override def newThread(r: Runnable): Thread = {
            val t = d.newThread(r)
            t.setName(getName())
            t.setDaemon(true)
            t
          }
        }
      )
  }

  @TearDown
  def close(): Unit = {
    scheduler.shutdown()
  }

  def getF[A](delay: Long)(result: => A): Future[A] = {
    if (delay <= 0L) {
      Future.successful(result)
    } else {
      val p = Promise[A]()
      scheduler.schedule(
        () => p.success(result),
        delay,
        TimeUnit.MILLISECONDS
      )
      p.future
    }
  }

}
