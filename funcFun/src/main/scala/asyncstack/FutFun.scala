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
  def myCalc() = async { await(foo)+await(bar)  }

  val tp = "html"
  println(ap.execute(s"start,event=cpu,cstack=fp,interval=5ms,file=boffo.$tp"))
  val calcs = (1 to 10).map( _ => myCalc())
  println(calcs.map(Await.result(_, Duration.Inf)).sum)
  println(ap.execute(s"stop,file=boffo.$tp"))


  // Burn cpu in a way that shows a c stack
  def burnCpu(ms: Long, x0: Double): Double = {
    val t = System.nanoTime() + ms * 1000 * 1000
    var x = x0
    while(System.nanoTime() < t) {
      var i = 10000
      while (i > 0) {
        x = x * 1.1
        x = Math.sin(x)
        i -= 1;
        x *= (1.0/1.1)
      }
    }
    x
  }
}


