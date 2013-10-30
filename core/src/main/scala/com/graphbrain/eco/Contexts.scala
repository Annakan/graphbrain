package com.graphbrain.eco

import scala.collection.mutable

class Contexts(val prog: Prog, val sentence: Words) {
  val ctxts = mutable.ListBuffer[Context]()

  private val addCtxts = mutable.ListBuffer[Context]()
  private val remCtxts = mutable.ListBuffer[Context]()

  def addContext(c: Context) = addCtxts += c
  def remContext(c: Context) = remCtxts += c

  def applyChanges() = {
    for (c <- addCtxts) ctxts += c
    for (c <- remCtxts) ctxts -= c
    addCtxts.clear()
    remCtxts.clear()
  }

  override def toString = {
    val sb = new mutable.StringBuilder()
    sb.append("Contexts: " + sentence)
    for (c <- ctxts)
      sb.append(c)
    sb.toString()
  }
}

object Contexts {
  def apply(prog: Prog, w: Words) = new Contexts(prog, w)
}