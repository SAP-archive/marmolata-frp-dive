package com.sap.marmolata.react.api.tests

import cats.Eq
import com.sap.marmolata.react.api.ReactiveDeclaration
import com.sap.marmolata.react.api.ReactiveLibrary.{Cancelable, Observable}
import org.scalacheck.{Arbitrary, Gen, Test}
import org.scalatest._
import com.sap.marmolata.react.react.core.ReactiveDeclaration
import com.sap.marmolata.react.react.core.ReactiveLibrary.{Annotation, Cancelable, Observable}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.ref.WeakReference

trait CollectValues[A] extends mutable.Buffer[A] {
  var ref: Cancelable
}

trait ReactiveLibraryName { def name: String }

object SelfRx extends ReactiveLibraryName { def name: String = "selfrx implementation" }
object ScalaRx extends ReactiveLibraryName { def name: String = "Scala.Rx wrapper" }
object MetaRx extends ReactiveLibraryName { def name: String = "MetaRxImpl" }

trait ReactiveLibraryTests extends FlatSpec with Matchers {

  def reactiveLibrary_ : ReactiveDeclaration
  final val reactiveLibrary: ReactiveDeclaration = reactiveLibrary_

  def collectValues[A](x: Observable[A]): CollectValues[A] = {
    val result = new mutable.ArrayBuffer[A]() with CollectValues[A] { var ref: Cancelable = null }
    result.ref = x.observe { (v: A) => result += v }
    result
  }

  def pendingFor(libraries: ReactiveLibraryName*)(f: => Unit): Unit = {
    if (libraries.exists(_.name == reactiveLibrary.implementationName)) {
      pendingUntilFixed(f)
    } else {
      f
    }
  }

  object DeprecationForwarder {
    // using workaround for https://issues.scala-lang.org/browse/SI-7934
    // this is completely ridicoulous but appearantly
    // noone cares enough
    @deprecated("", "")
    class C {
      import reactiveLibrary._
      def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = f.toEvent
    }
    object C extends C
  }

  // TODO this is probably somewhere in the standard library
  // TODO I have no clue where the bug is that makes it necessary to execute recurisive calls directly
  //      but otherwise the strangeFlatMapBug test doesn't work
  class SimpleExecutionContext extends ExecutionContext {
    self =>
    private var queue: mutable.Buffer[Runnable] = mutable.Buffer.empty
    private var currentlyExecuting = false

    def execute(runnable: Runnable): Unit = {
      queue += runnable
    }

    def reportFailure(cause: Throwable): Unit = {
      info(cause.getMessage)
    }

    final def runQueue(): Unit = {
      runQueue(12)
    }

    @tailrec
    private def runQueue(recursion: Int): Unit = {
      val theQueue = queue
      queue = mutable.Buffer.empty
      try {
        theQueue foreach (_.run())
      }
      catch {
        case t: Throwable =>
          info(s"major problem: ${t}")
      }

      if (!queue.isEmpty) {
        if (recursion > 0) {
          runQueue(recursion - 1)
        }
        else {
          fail("recursion in ExecutionContext detected")
        }
      }
    }
  }
}

case class TestConfiguration(lazyMap: Boolean, forwardExceptions: Boolean)

object TestConfiguration {
  def all: TestConfiguration = TestConfiguration(true, true)
}

object DeprecatedTest extends Tag("com.sap.marmolata.react.react.deprecated")
