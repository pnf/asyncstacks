package asyncstack.demo


import scala.concurrent.{Await, Future}
import scala.async.Async.await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration



object Test extends App {
  import asyncstack._
  val ap = apInstance

  def expensive1 = burnCpu(1000, 1.1)
  def expensive2 = burnCpu(500, 1.2)
  def foo: Future[Double] = async { expensive1 }
  def bar: Future[Double] = async { await(foo) + expensive2 + expensive1  }
  def boo: Future[Double] = async { await(bar) * 2.0  }
  val myCalc = async { await(foo)+await(bar)  }

  println(ap.execute("start,event=cpu,interval=5m"))
  println(Await.result(myCalc, Duration.Inf))
  println(ap.execute("stop,file=boffo.html"))


  // Burn cpu in a way that shows a c stack
  def burnCpu(ms: Long, x0: Double): Double = {
    val t = System.nanoTime() + ms * 1000 * 1000
    var x = x0
    while(System.nanoTime() < t) { x = Math.sin(Math.pow(x,1.1)) + 1.0  }
    x
  }
}


