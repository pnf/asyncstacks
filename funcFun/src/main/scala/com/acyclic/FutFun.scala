package com.acyclic

import com.acyclic.async.TaggedAsync

import scala.concurrent.Await
import scala.concurrent.duration.Duration



object Test extends App {

  import com.acyclic.async.TaggedAsync._

  import scala.async.Async.await
  import scala.concurrent.ExecutionContext.Implicits.global

  val ap = TaggedAsync.ap

  def foo = async {
    burn(1000, 1.3)
  }
  def bar = async {
    await(foo) + 1.0
  }
  def boo = async {
    await(bar) * 2.0
  }

  println(ap.execute("start,event=cpu,interval=5m"))
  println(Await.result(boo, Duration.Inf))
  println(ap.execute("stop,file=boffo.html"))

  def burn(ms: Long, x0: Double): Double = {
    val t = System.nanoTime() + ms * 1000 * 1000
    var x = x0
    while(System.nanoTime() < t)
      x = Math.sin(x) + 1.0
    x
  }
}


/*
object FutureFun extends  App {

  def burn(ms: Long, x0: Double): Double = {
    val t = System.nanoTime() + ms * 1000 * 1000
    var x = x0
    while(System.nanoTime() < t)
      x = Math.sin(x) + 1.0
    x
  }

  val x = for {
    a <- Future(3.0)
    b <- Future(a + 42.0)
    c <- Future(burn(3600000l, 1.5))
  } yield c



  Await.result(x, Duration.Inf)


}
*/
