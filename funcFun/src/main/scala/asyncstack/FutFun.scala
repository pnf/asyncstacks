package asyncstack.demo


import one.profiler.AsyncProfiler

import scala.concurrent.{Await, Future}
import scala.async.Async.await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import jdk.jfr

import java.util.concurrent.{Executors, StructuredTaskScope}
import scala.util.Try


trait TestStuff  {
  import asyncstack._
  val ap = apInstance

  val executor = Executors.newVirtualThreadPerTaskExecutor()
  implicit val ec = ExecutionContext.fromExecutorService(executor)

  def expensive1 = {
    val x = burnCpu(5000, 1.1)
    Thread.sleep(1)
    x + burnCpu(5000, 1.1)
  }
  def expensive2 = expensive1

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

object Test extends TestStuff with App {
  import asyncstack._

  def foo: Future[Double] = async {
    expensive1
  }

  def bar(i: Int): Future[Double] = async {
    await(foo) + expensive2 + expensive1
  }

  def myCalc(i: Int) = {
    async {
      await(foo) + await(bar(i))
    }
  }

  val tp = "html"
  println(ap.execute(s"start,event=cpu,cstack=dwarf,interval=10ms,file=boffo.$tp"))
  for (i <- 1 to 2) {
    val calcs = (1 to 10).map(myCalc)
    println(s"$i " + calcs.map(f => Try(Await.result(f, Duration.fromNanos(1e9)))))
  }
  print(ap.execute(s"dump,file=boffo.$tp"))
  println(ap.dumpTraces(10))
  println(ap.execute(s"stop"))
}

object Loomy extends TestStuff with App {

  def myCalc() = expensive1 + expensive2
  val out = "boffo.html"
  var scope = new StructuredTaskScope.ShutdownOnFailure()
  println(ap.execute(s"start,event=cpu,cstack=dwarf,interval=10ms,file=$out"))
  val tasks = (1 to 5).map(_ => scope.fork(myCalc _))
  print(ap.execute(s"dump,file=$out"))

  scope.join()



}
