package me.jeffshaw.futures.unordered

sealed trait CollectCondition(val maybeMax: Option[Int]) {
  protected def checkMax(): Unit = {
    for (max <- maybeMax) {
      if (max < 1) {
        throw new IllegalArgumentException("max must be > 0")
      }
    }
  }

  checkMax()
}

object CollectCondition {
  case class Custom(
    collect: Throwable => Boolean,
    override val maybeMax: Option[Int] = None
  ) extends CollectCondition(maybeMax)

  abstract case class Probability private (
    p: Double,
    override val maybeMax: Option[Int]
  ) extends CollectCondition(maybeMax) {
    def copy(
      p: Double = this.p,
      maybeMax: Option[Int] = this.maybeMax
    ): CollectCondition = {
      if (this.p == p && this.maybeMax == maybeMax) {
        this
      } else {
        Probability(p, maybeMax)
      }
    }
  }

  object Probability {
    def apply(p: Double, maybeMax: Option[Int] = None): CollectCondition = {
      if (p < 0D || p > 1D) {
        throw new IllegalArgumentException("p must be >= 0 and <= 1")
      }
      p match {
        case 0D =>
          Never
        case 1D =>
          maybeMax match {
            case Some(max) =>
              UpTo(max)
            case None =>
              Always
          }
        case _ =>
          new Probability(p, maybeMax) { }
      }
    }
  }

  case object Never extends CollectCondition(Some(0)) {
    override protected def checkMax(): Unit = ()
  }

  case object Always extends CollectCondition(None)

  case class OneEach(override val maybeMax: Option[Int] = None) extends CollectCondition(maybeMax)

  abstract case class UpTo private (n: Int) extends CollectCondition(Some(n)) {
    def copy(n: Int = this.n): CollectCondition = {
      if (this.n == n) {
        this
      } else UpTo(n)
    }
  }

  object UpTo {
    def apply(n: Int): CollectCondition = {
      n match {
        case 0 =>
          Never
        case 1 =>
          One
        case n =>
          new UpTo(n) { }
      }
    }

    val One: UpTo = new UpTo(1) { }
  }
}
