# Unordered Future Utilities

This contains lower overhead methods for working with collections of `Future`s than `Future.{fold, foldLeft, sequence, traverse}`. The tradeoff is that the order of the `Future`s is not preserved in the aggregation.

## Usage

There are two APIs. One is for prefix method calls, and one is for suffix.

### Prefix API

```scala
import me.jeffshaw.futures.unordered

def toVector(futures: Iterator[Future[A]]): Vector[A] = {
  unordered.to(futures, Vector).await()
}
```

### Suffix API

```scala
import me.jeffshaw.futures.Unordered._

def toVector(futures: Iterator[Future[A]]): Vector[A] = {
  futures.unordered.to(Vector).await()
}
```

## Waiting for the Result

Immediately upon calling a method in the `unordered` package, it starts to execute `Futures`.
The method call blocks until the iterator is consumed. The result will be a `WaitMethods` instance.
It provides methods `await(Duration)` to block until the final value is available,
`future()` if you don't want to block, or you can use its `Iterable` interface to use it in
`for` comprehensions.

If you don't want to wait for all Futures to begin executing, wrap the call in `Future`

```scala
import me.jeffshaw.futures.Unordered._

def toVectorSync(futures: Iterator[Future[A]]): Vector[A] = {
  futures.unordered.to(Vector).await()
}

def toVectorAsync(futures: Iterator[Future[A]]): Future[Vector[A]] = {
  for {
    await <- Future(futures.unordered.to(Vector))
    result <- await.future()
  } yield result 
}

def toVectorIterable(futures: Iterator[Future[A]]): Vector[A] = {
  for {
    vector <- futures.unordered.to(Vector)
    element <- vector
  } yield element
}

def toVectorIterableWithTimeout(futures: Iterator[Future[A]]): Vector[A] = {
  for {
    vector <- futures.unordered.to(Vector).iterator(Duration("5s"))
    element <- vector.iterator
  } yield element
}.toVector
```

## Examples

### Fold

```scala
def concat(futures: Iterator[Future[String]]): String = {
  futures.unordered.fold("")(_ + _).await()
}
```

### Ignore

Use this if you don't care about the results, just that the Futures completed.

```scala
def concat(futures: Iterator[Future[String]]): String = {
  futures.unordered.ignore().await()
}
```

### Specialized Methods

If you have a collection of `Future[Int]`, `Long`, or `Double`, you can use the specialized methods to avoid boxing.

```scala


def sum(futures: Iterator[Future[Long]]): Long = {
  unordered.long.sum(futures).await()
}
```

You can call the generic `sum` method to do the same thing. It will use the `Numeric` instance to determine which specialized
method to use, if any.

```scala
import me.jeffshaw.futures.Unordered._

def sum(futures: Iterator[Future[Long]]): Long = {
  futures.unordered.sum().await()
}
```

There are also specialized methods if your futures do not result in `Int`, `Long`, or `Double`, but your aggregating method does.

```scala
import me.jeffshaw.futures.Unordered._

def totalLength(futures: Iterator[Future[String]]): Long = {
  futures.unordered.foldToLong(0L)(_ + _.length).await()
}
```

### Run

This is the most general operation this API provides. You have to provide the function calls for what happens when a `Future` completes, and how to get the result.

The following example gives the total number of `Futures` that completed successfully.

```scala
import java.util.concurrent.atomic.LongAdder
import me.jeffshaw.futures.Unordered._

def count[A](futures: Iterator[Future[A]]): Long = {
  val sum = new LongAdder()
  futures.unordered.run(
    add = (value: A) => sum.increment(),
    get = sum.sum
  )
}
```

## Error Handling

By default, the runtime will stop executing `Futures` when the first one fails. It will also throw only the first exception.

You can configure it to record any number of failures, but it always throws an exception if the `get` method passed to the runtime fails.

### Allow no exceptions, and record only the first exception

This is the default.

```scala
def safeFold[A, B](futures: Iterator[Future[A]], init: B)(accum: (B, A) => B): WaitMethods[B] = {
  futures.unordered.fold(
    init = init,
    failAfter = FailAfter.Exact.One, // This can be omitted, since it's the default.
    collectCondition = CollectCondition.UpTo.One // This can be omitted, since it's the default.
  )(accum)
}
```

### Allow any number of exceptions, record only the first exception

```scala
import me.jeffshaw.futures.Unordered._

def unsafeFold[A, B](futures: Iterator[Future[A]], init: B)(accum: (B, A) => B): WaitMethods[B] = {
  futures.unordered.fold(
    init = init,
    failAfter = FailAfter.Never,
    collectCondition = CollectCondition.UpTo.One
  )(accum)
}
```

### Allow any number of exceptions, record all exceptions

```scala
import me.jeffshaw.futures.Unordered._

def unsafeFold[A, B](futures: Iterator[Future[A]], init: B)(accum: (B, A) => B): WaitMethods[B] = {
  futures.unordered.fold(
    init = init,
    failAfter = FailAfter.Never,
    collectCondition = CollectCondition.Always
  )(accum)
}
```

### Fail if at least half the Futures fail, record half of exceptions

```scala
import me.jeffshaw.futures.Unordered._

def unsafeFold[A, B](futures: Iterator[Future[A]], init: B)(accum: (B, A) => B): WaitMethods[B] = {
  futures.unordered.fold(
    init = init, 
    failAfter = FailAfter.Proportion(0.5), 
    collectCondition = CollectCondition.Probability(0.5)
  )(accum)
}
```

### Allow any number of exceptions, record one of each exception type, up to 50 exceptions.

```scala
import me.jeffshaw.futures.Unordered._

def unsafeFold[A, B](futures: Iterator[Future[A]], init: B)(accum: (B, A) => B): WaitMethods[B] = {
  futures.unordered.fold(
    init = init,
    failAfter = FailAfter.Never,
    collectCondition = CollectCondition.OneEach(Some(50))
  )(accum)
}
```

### Allow up to 49 exceptions, with custom exception handling.

```scala
import me.jeffshaw.futures.Unordered._

def unsafeFold[A, B](futures: Iterator[Future[A]], init: B)(accum: (B, A) => B): WaitMethods[B] = {
  def printException(t: Throwable): Boolean = {
    t.printStackTrace()
    true
  }

  futures.unordered.fold(
    init = init,
    failAfter = FailAfter.Exact(50),
    collectCondition = CollectCondition.Custom(printException)
  )(accum)
}
```

## Concurrency Limiting

You can limit the number of `Futures` that are executing at any given time. Simply override the `maybeMaxConcurrency` argument.

```scala
import me.jeffshaw.futures.Unordered._

def slowFold[A, B](futures: Iterator[Future[A]], init: B)(accum: (B, A) => B): WaitMethods[B] = {
  futures.unordered.fold(
    init = init,
    maybeMaxConcurrency = Some(10)
  )(accum)
}
```
