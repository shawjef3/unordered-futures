package me.jeffshaw.futures.unordered

import org.openjdk.jmh.annotations.Param

trait Params {

  @Param(Array("0", "1", "10", "100", "1000", "10000"))
  var size: Int = _

  @Param(Array("0", "1", "10", "100"))
  var delay: Long = _

}
