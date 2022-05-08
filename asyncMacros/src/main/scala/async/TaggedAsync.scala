package com.acyclic.async

import sourcecode.Enclosing

import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox


object TaggedAsync {

  private val localStack = new ThreadLocal[(Long, List[Enclosing])] {
    override def initialValue() = (0, Nil)
  }

  private class RunnableWithStack(hash: Long, val stack: List[Enclosing], defer: Runnable) extends Runnable {
    override def run(): Unit = {
      val prev = localStack.get()
      localStack.set((hash, stack))
      defer.run()
      localStack.set(prev)
    }
  }

  class LocalContext(parent: ExecutionContext, loc: Enclosing) extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = {
      val (currentHash, currentStack) = localStack.get()
      val hash = currentHash ^ loc.hashCode()
      val stack = loc :: currentStack
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
      q"scala.async.Async.async($body)(new com.acyclic.async.TaggedAsync.LocalContext($execContext, $loc))"
    )
  }
}
