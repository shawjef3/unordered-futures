package me.jeffshaw.futures.unordered

private trait ExceptionMatcher {
  def check(t: Throwable): Unit
  def getMatched(): Boolean
}

private object ExceptionMatcher {
  object None extends ExceptionMatcher {
    override def check(t: Throwable): Unit = ()

    override def getMatched(): Boolean = false
  }

  class Predicate(p: Throwable => Boolean) extends ExceptionMatcher {
    @volatile
    private var matched = false
    override def check(t: Throwable): Unit = {
      if (p(t)) {
        matched = true
      }
    }

    override def getMatched(): Boolean = {
      matched
    }
  }
}
