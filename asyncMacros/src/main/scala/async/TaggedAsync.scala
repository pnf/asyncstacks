import one.profiler.AsyncProfiler
import sourcecode.Enclosing

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox


package object asyncstack {
  private val localStack = new ThreadLocal[(Long, List[(String, Long)])] {
    override def initialValue() = (0, Nil)
  }

  lazy val apInstance = AsyncProfiler.getInstance("/home/pnf/dev/async-profiler/build/libasyncProfiler.so")
  private lazy val rwsRunId = AsyncProfiler.getMethodID(classOf[RunnableWithStack], "run", "()V")
  private val instId = new AtomicLong(0)

  private class RunnableWithStack(hash: Long, val stack: List[(String, Long)], defer: Runnable) extends Runnable {
    override def run(): Unit = {
      val prev = localStack.get()
      val signal = instId.incrementAndGet()
      AsyncProfiler.setAwaitStackId(hash, signal, rwsRunId)
      localStack.set((hash, stack))
      defer.run()
      if(signal == AsyncProfiler.getAwaitSampledSignal) {
        val s = (hash :: stack.map(_._2)).toArray
        apInstance.saveAwaitFrames(2, s, s.size)
      }
      localStack.set(prev)
    }
  }

  class LocalContext(parent: ExecutionContext, loc: Enclosing) extends ExecutionContext {
    val name = loc.value
    val id: Long = AsyncProfiler.saveString(name)
    val (currentHash, currentStack) = localStack.get()
    val hash = currentHash ^ loc.hashCode()
    val stack =  (name, id) :: currentStack
    override def execute(runnable: Runnable): Unit = {
      val r = new RunnableWithStack(hash, stack, runnable)
      parent.execute(r)
    }

    override def reportFailure(cause: Throwable): Unit =
      parent.reportFailure((cause))
  }


  def async[T](body: => T)(implicit execContext: ExecutionContext, loc: Enclosing): Future[T] = macro asyncImpl[T]

  def asyncImpl[T: c.WeakTypeTag](c: blackbox.Context)
                                 (body: c.Expr[T])
                                 (execContext: c.Expr[ExecutionContext], loc: c.Expr[Enclosing]): c.Expr[Future[T]]= {
    import c.universe._
    c.Expr[Future[T]](
      q"scala.async.Async.async($body)(new asyncstack.LocalContext($execContext, $loc))"
    )
  }
}
