import one.profiler.AsyncProfiler
import sourcecode.Enclosing


import java.lang.foreign.{MemorySegment, ValueLayout}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import sun.misc.Unsafe

package object asyncstack {
  private val localStack = new ThreadLocal[(Long, List[(String, Long)])] {
    override def initialValue = (0, Nil)
  }
  val apInstance = AsyncProfiler.getInstance("/home/pnf/dev/async-profiler/build/lib/libasyncProfiler.so")
  private val rwsRunId = apInstance.getMethodID(classOf[RunnableWithStack], "run", "()V", false)
  private val instId = new AtomicLong(0)

  val apSize = apInstance.initAwaitData(1389)

  @volatile private var deferring = true
  private val deferThread = new Thread {
    override def run(): Unit = {
      while(deferring) apInstance.recordDeferred(100, 100)
    }
  }
  deferThread.setDaemon(true)
  deferThread.setName("deferral")
  deferThread.start()

  private val LONG_PHI = 0x9E3779B97F4A7C15L
  private def mix(x: Long): Long = {
    var h = x * LONG_PHI
    h ^= h >>> 32;
    h ^ (h >>> 16);
  }

  private def combine(hash: Long, rhs: Long): Long =  hash ^ (mix(rhs) + LONG_PHI + (hash << 6) + (hash >> 2))

  private val fu = classOf[Unsafe].getDeclaredField("theUnsafe")
  fu.setAccessible(true)
  private val U = fu.get(0).asInstanceOf[Unsafe]

  def getVals = {
    val address = apInstance.getAwaitDataAddress
    val view = MemorySegment.ofAddress(address).reinterpret(8)
    view.getAtIndex(ValueLayout.JAVA_LONG, 0);
  }

  private class RunnableWithStack(hash: Long, val stack: List[(String, Long)], defer: Runnable) extends Runnable {
    override def run(): Unit = {
      val prev = localStack.get()
      val signal = instId.incrementAndGet()
      val addr = apInstance.getAwaitDataAddress
      val segment = MemorySegment.ofAddress(addr).reinterpret(8*(apSize + 6))
      segment.setAtIndex(ValueLayout.JAVA_LONG, 0, rwsRunId)
      segment.setAtIndex(ValueLayout.JAVA_LONG, 1, signal)
      segment.setAtIndex(ValueLayout.JAVA_LONG, 3, hash);
      segment.setAtIndex(ValueLayout.JAVA_LONG, 4, 0);
      localStack.set((hash, stack))
      defer.run()
      segment.setAtIndex(ValueLayout.JAVA_LONG, 1, 0)
      val signaled = segment.getAtIndex(ValueLayout.JAVA_LONG, 2)
      if(signal == signaled) {
        val s = (hash :: stack.map(_._2)).toArray
        apInstance.saveAwaitFrames(2, s, s.size)
      }
      localStack.set(prev)
    }
  }

  class LocalContext(parent: ExecutionContext, loc: Enclosing) extends ExecutionContext {
    val name = loc.value
    val id: Long = apInstance.saveString("@" + name)
    val (currentHash, currentStack) = localStack.get()
    val hash = combine(currentHash,loc.hashCode())
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
