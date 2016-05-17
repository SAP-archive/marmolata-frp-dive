package react.LibTests

import org.scalatest.{FlatSpec, AsyncFlatSpec, Matchers}
import react.ReactiveLibrary
import react.ReactiveLibrary.{Cancelable, Observable}
import scala.collection.mutable.MutableList
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.ref.WeakReference
import scala.scalajs.js.timers._
import scala.collection.mutable

object ReactLibraryTests {
  def sleep(duration: FiniteDuration): Future[Unit] = {
    val result = Promise[Unit]()
    setTimeout(duration) {
      result.success()
    }
    result.future
  }
}

trait ReactLibraryTests {
  self: FlatSpec with Matchers =>

  def runLibraryTests(reactLibrary: ReactiveLibrary) {
    import reactLibrary._
    import ReactLibraryTests._

    def collectValues[A](x: Observable[A]): mutable.Seq[A] = {
      val result = new mutable.MutableList[A]() {
        var ref: Cancelable = null
      }
      val obs = x.observe { (v: A) => result += v }
      result.ref = obs
      result
    }

    it should "not directly trigger its value when used as event, but directly trigger as variable" in {
      val var1 = Var(5)
      val asEvent = toEvent(var1)

      val l1 = collectValues(var1)
      val le = collectValues(asEvent)

      var1.update(10)

      le shouldEqual List(10)
      l1 shouldEqual List(5, 10)
    }

    it should "only update the value once in a rhombus" in {
      val v = Var(7)
      val w = v.map(x => x + 1)
      val x = v.map(x => x + 2)
      val y = for {
        ww <- w
        xx <- x
      } yield (ww + xx)

      val l = collectValues(y)

      v.update(8)
      v.update(9)

      l shouldEqual List(8 + 9, 9 + 10, 10 + 11)
    }

    it should "allow zipping" in {
      val v1 = Var(1)
      val v2 = Var("a")

      val zipped = v1 zip v2
      val l = collectValues(zipped)

      v1.update(10)
      v2.update("z")

      def g(x: (Int, Int)): Int = { 6 }

      l shouldEqual List((1, "a"), (10, "a"), (10, "z"))
    }

    it should "allow update a variable inside map" in {
      val v1 = Var(0)
      val v2 = Var(-1)
      val l = collectValues(v2)

      v1.map(v2.update(_))

      (1 to 5) foreach (v1.update)

      l shouldEqual List(-1, 0, 1, 2, 3, 4, 5)
    }

    it should "not trigger when a value isn't changed" in {
      val v = Var(0)
      val l = collectValues(v)

      v.update(0)
      v.update(0)

      l shouldEqual List(0)
    }

    it should "not trigger when a value isn't changed in a dependent signal" in {
      val v = Var(0)
      val r = v.map(Function.const(3))
      val l = collectValues(r)

      v.update(1)
      v.update(2)

      l shouldEqual List(3)
    }

    it should "do trigger when a value isn't changed as an event" in {
      val v = Var(0)
      val r = toEvent(v).map(Function.const(0))
      val l = collectValues(r)

      v.update(2)
      v.update(3)

      l shouldEqual List(0, 0)
    }

    it should "allow to update a variable inside observe" in {
      val v = Var(0)
      val w = Var(-1)
      val l = collectValues(w)

      v.observe { w.update(_) }

      (1 to 5) foreach (v.update)

      l shouldEqual List(-1, 0, 1, 2, 3, 4, 5)
    }

    it should "allow to update a variable inside observe, but then doesn't need to be atomic" in {
      val v = Var(0)
      val v2 = Var(-1)
      v.observe { v2.update(_) }

      val r = v zip v2
      val l = collectValues(r)

      v.update(1)
      v.update(2)

      l should contain inOrder((0,0), (1,1), (2,2))
    }


    it should "trigger futures" in {
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow

      val p = Promise[Int]()
      val v = futureToEvent(p.future)
      val l = collectValues(v)
      p success 10
      l shouldEqual List(10)
    }

    it should "handle futures which come in out of order" in {
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow

      val promises = new Array[Promise[Int]](10)
      (0 to 9) foreach { i => promises(i) = Promise[Int]() }
      val v = Var(0)
      val w = toEvent(v).flatMap { i =>
        futureToEvent(promises(i).future)
      }

      val l = collectValues(w)

      v.update(1)
      v.update(2)
      v.update(3)

      promises(1) success 1
      promises(3) success 3
      promises(2) success 2

      v.update(4)
      promises(4) success 4

      l shouldEqual List(3, 4)
    }


    ignore should "get times when clicked" in {
      val time = Var(0)
      val click = Var()

      val result = Var(time.now)
      click.map { (Unit) => result.update(time.now) }

      val l = collectValues(result)

      (1 to 100) foreach { x =>
        time.update(x)
        if (x % 30 == 0) { click.update() }
      }

      l shouldEqual List(0, 30, 60, 90)
    }

    ignore should "not leak" in {
      val v = Var(0)
      var s = false
      val wr = WeakReference(v.map { _ => s = true })
      (1 to 20) foreach { v.update(_) }
      s shouldEqual true

      while (wr.get.isDefined) {
        (1 to 20) foreach {
          v.update(_)
        }
        System.gc()
      }

      s = false
      (1 to 20) foreach {
        v.update(_)
      }

      s shouldEqual false
    }

    it should "allow exceptions" in {
      val v = Var(0)
      val w = v.map { x => throw new Exception("Hi") }
      v.update(7)
      intercept[Exception] {
        w.now
      }
    }
  }
}
