package react.LibTests

import algebra.Eq
import org.scalacheck.{Gen, Arbitrary}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Succeeded, FlatSpec, AsyncFlatSpec, Matchers}
import react.{ReactiveLibraryUsage, ReactiveLibrary}
import react.ReactiveLibrary.{Cancelable, Observable}
import scala.annotation.tailrec
import scala.collection.mutable.MutableList
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.ref.WeakReference
import scala.collection.mutable

object ReactLibraryTests {
  trait CancelableTrait {
    var ref: Cancelable
  }
}

// TODO this is probably somewhere in the standard library
// TODO I have no clue where the bug is that makes it necessary to execute recurisive calls directly
//      but otherwise the strangeFlatMapBug test doesn't work
class SimpleExecutionContext extends ExecutionContext {
  self =>
  private var queue: List[(Runnable, String)] = List.empty
  private var currentlyExecuting = false

  def execute(runnable: Runnable): Unit = {
    //if (currentlyExecuting)
    //  runnable.run()
    //else
      queue = queue :+ (runnable, "")
  }

  def subExecutor(name: String): ExecutionContext =
    new ExecutionContext {
      override def execute(runnable: Runnable): Unit =
        //if (currentlyExecuting)
        //  runnable.run()
        //else
          queue = queue :+ (runnable, name)

      override def reportFailure(cause: Throwable): Unit = self.reportFailure(cause)
    }

  def reportFailure(cause: Throwable): Unit = {
    println(cause)
  }

  final def runQueue(name: String = "", recursive: Int = 0): Boolean = {
    println(s"run queue ${name} with ${queue.length} items ${recursive}")
    val theQueue = queue
    currentlyExecuting = true
    queue = List.empty
    try {
      theQueue foreach { case (r, s) => r.run();
        if (s != "") {
          println(s"executed ${s}")
        }
      }}
    catch {
      case t: Throwable =>
        println(s"major problem: ${t}")
    }


    try {
      if (!queue.isEmpty && recursive < 50)
        runQueue(name, recursive + 1)
      else
        queue.isEmpty
    }
    finally {
      if (recursive == 0)
        currentlyExecuting = false
    }
  }
}

trait ReactLibraryTests {
  self: FlatSpec with Matchers =>

  def collectValues[A](x: Observable[A]): mutable.Seq[A] with ReactLibraryTests.CancelableTrait = {
    val result = new mutable.MutableList[A]() with ReactLibraryTests.CancelableTrait {
      var ref: Cancelable = null
    }
    val obs = x.observe { (v: A) => result += v }
    result.ref = obs
    result
  }


  val reactLibrary: ReactiveLibrary with ReactiveLibraryUsage = reactLibrary_
  def reactLibrary_ : ReactiveLibrary with ReactiveLibraryUsage

  private object DeprecationForwarder {
    // using workaround for https://issues.scala-lang.org/browse/SI-7934
    // this is completely ridicoulous but appearantly
    // noone cares enough
    @deprecated("", "")
    class C {
      import reactLibrary._
      def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = f.toEvent
    }
    object C extends C
  }


  def runLibraryTests: Unit = {
    import reactLibrary._
    import reactLibrary.syntax._
    import ReactLibraryTests._


    it should "not trigger this strange flatMap bug ;;;;" in {
      implicit val queue = new SimpleExecutionContext()
      val e = EventSource[Int]
      val p = Promise[Int]
      val f = DeprecationForwarder.C.futureToEvent(p.future)


      import unsafeImplicits.eventApplicative
      var number: Int = 0
      val r = e.flatMap {
        i =>
          number += 1
          DeprecationForwarder.C.futureToEvent(p.future)
      }
      val l = collectValues(r)


      e emit 0
      p success 10
      queue.runQueue("q2")
      number should be < 20

      l shouldEqual List(10)
    }


    it should "not directly trigger its value when used as event, but directly trigger as variable" in {
      val var1 = Var(5)
      val asEvent = var1.toEvent

      val l1 = collectValues(var1)
      val le = collectValues(asEvent)

      var1.update(10)

      le shouldEqual List(10)
      l1 shouldEqual List(5, 10)
    }

    it should "only update the value once in a rhombus" in {
      import unsafeImplicits.signalApplicative
      val v = Var(7)
      val w = v.map(x => x + 1)
      val x = v.map(x => x + 2)
      val y = w.flatMap { ww =>
        x.map(ww + _)
      }

      val l = collectValues(y)

      v.update(8)
      v.update(9)

      l shouldEqual List(8 + 9, 9 + 10, 10 + 11)
    }

    it should "allow zipping" in {
      val v1 = Var(1)
      val v2 = Var("a")

      val zipped = v1.map2(v2)((_, _))
      val l = collectValues(zipped)

      v1.update(10)
      v2.update("z")

      def g(x: (Int, Int)): Int = {
        6
      }

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
      val r = v.toEvent.map(Function.const(0))
      val l = collectValues(r)

      v.update(2)
      v.update(3)

      l shouldEqual List(0, 0)
    }

    it should "allow to update a variable inside observe" in {
      val v = Var(0)
      val w = Var(-1)
      val l = collectValues(w)

      v.observe {
        w.update(_)
      }

      (1 to 5) foreach (v.update)

      l shouldEqual List(-1, 0, 1, 2, 3, 4, 5)
    }

    it should "allow to update a variable inside observe, but then doesn't need to be atomic" in {
      val v = Var(0)
      val v2 = Var(-1)
      v.observe {
        v2.update(_)
      }

      val r = v product v2
      val l = collectValues(r)

      v.update(1)
      v.update(2)

      l should contain inOrder((0, 0), (1, 1), (2, 2))
    }


    it should "trigger futures" in {
      implicit val queue = new SimpleExecutionContext()

      val p = Promise[Int]()
      val v = p.future.toEvent
      val l = collectValues(v)
      queue.runQueue("trigger 1")

      p success 10
      queue.runQueue("trigger 2")

      l shouldEqual List(10)
    }

    it should "handle futures which come in out of order" in {
      import unsafeImplicits.eventApplicative
      val queue = new SimpleExecutionContext()

      val promises = new Array[Promise[Int]](10)
      (0 to 9) foreach { i => promises(i) = Promise[Int]() }
      val v = Var(0)
      val w = v.toEvent.flatMap { i =>
        DeprecationForwarder.C.futureToEvent(promises(i).future)(queue.subExecutor(s"${i}"))
      }

      val l = collectValues(w)

      queue.runQueue("g0") shouldEqual true
      v.update(1)
      queue.runQueue("g1") shouldEqual true

      v.update(2)
      queue.runQueue("g2") shouldEqual true

      v.update(3)
      queue.runQueue("g3") shouldEqual true

      promises(1) success 1
      queue.runQueue("g4") shouldEqual true


      promises(3) success 3
      queue.runQueue("g5") shouldEqual true


      promises(2) success 2
      queue.runQueue("g6") shouldEqual true

      v.update(4)
      promises(4) success 4

      queue.runQueue("handle") shouldEqual true

      l shouldEqual List(3, 4)
    }

    it should "not trigger this strange flatMap bug" in {
      implicit val queue = new SimpleExecutionContext()
      val v = EventSource[Int]
      val p = Promise[Int]
      import unsafeImplicits.eventApplicative
      val r = v.flatMap { i => DeprecationForwarder.C.futureToEvent(p.future) }
      v emit 10
      queue.runQueue("q1")
      val l = collectValues(r)
      p success 7
      queue.runQueue("q2")

      l shouldEqual List(7)
    }

    it should "get times when clicked" in {
      val time = Var(0)
      val click = EventSource[Unit]()

      val result = Var(time.now)
      click.observe { _ => result.update(time.now) }

      val l = collectValues(result)

      (1 to 100) foreach { x =>
        time.update(x)
        if (x % 30 == 0) {
          click emit (())
        }
      }

      l shouldEqual List(0, 30, 60, 90)
    }

    ignore should "not leak" in {
      val v = Var(0)
      var s = false
      val wr = WeakReference(v.map { _ => s = true })
      (1 to 20) foreach {
        v.update(_)
      }
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
      Succeeded
    }

    ignore should "compute map lazily" in {
      val v = Var(0)
      var counter = 0
      val w = v.map { x => counter += 1; x }
      v.update(7)

      counter shouldBe 0
    }

    it should "not observe after killed anymore" in {
      val v = Var(0)
      val l = mutable.MutableList[Int]()
      val c = v.observe {
        l += _
      }
      v.update(3)
      c.kill()
      v.update(6)

      l shouldEqual List(0, 3)
    }

    it should "not recalculate unused values to often" in {
      val v = Var(0)
      val w = v.map(_ + 1)
      var counter = 0

      import unsafeImplicits.signalApplicative

      v.flatMap { x =>
        val result = w.map {
          x + _
        }
        result.map(_ => counter += 1)
      }

      1 to 100 foreach {
        v := _
      }

      counter should be <= 500
    }

    it should "zip together events only if there's a previous value" in {
      import unsafeImplicits._
      val v1 = EventSource[Int]()
      val v2 = EventSource[Int]()
      val l = collectValues(v1 product v2)

      v1 emit 7
      v1 emit 8
      v2 emit 3
      v2 emit 5

      val l2 = collectValues(v1 product v2)

      v2 emit 99

      l shouldBe List((8, 3), (8, 5), (8, 99))
      l2 shouldBe List.empty
    }

    it should "not remember its value as an event (1)" in {
      import unsafeImplicits._
      val v = Var(7)
      val w = Var(8)
      val e = v.toEvent

      val l = collectValues(e product w.toEvent)

      w := 10
      w := 11
      v := 8
      v := 9

      l shouldBe List((8, 11), (9, 11))
    }

    it should "not remember its value as an event (2)" in {
      val v = Var(7)
      v.toEvent.toSignal(0).now shouldBe 0
    }

    it should "support reassignable signals" in {
      val v = ReassignableVar(0)
      val l = collectValues(v)
      val w = Var(7)

      v := 7
      v := 8

      v subscribe w
      w := 10
      w := 13
      v := 13
      w := 15

      l shouldEqual List(0, 7, 8, 7, 10, 13)
    }

    it should "support reassignable events" in {
      val v = ReassignableEvent[Int]
      val l = collectValues(v)

      val z1 = EventSource[Int]
      val z2 = EventSource[Int]

      v subscribe z1
      z1 emit 3
      z1 emit 5

      v subscribe z2
      z2 emit 5
      z1 emit 17
      z1 emit 29
      z2 emit 33

      v subscribe z1
      z1 emit 100
      z2 emit 1000

      l shouldEqual List(3, 5, 5, 33, 100)
    }

    it should "understand map" in {
      val v = EventSource[Int]
      val w = v.map(_ * 3)
      val l = collectValues(w)

      v emit 2
      v emit 10
      v emit 100

      l shouldEqual List(6, 30, 300)
    }

    it should "play well with merges" in {
      val e = EventSource[List[Either[Int, Int]]]

      val e1 = e map {
        _.collectFirst { case Left(x) => x }
      } mapPartial { case Some(x) => x }
      val e2 = e map {
        _.collectFirst { case Right(x) => x }
      } mapPartial { case Some(x) => x }

      val l = collectValues(e1 merge e2)

      e emit List(Left(3))
      e emit List(Right(5))

      e emit List(Left(7), Right(8))
      e emit List(Left(22))
      e emit List()
      e emit List(Left(9))

      l shouldEqual List(3, 5, 7, 22, 9)
    }

    it should "also emit the identical element" in {
      val e = EventSource[Unit]
      val l = collectValues(e)
      val v = ()
      e emit v
      e emit v
      e emit v

      l shouldEqual List((), (), ())
    }

    it should "use the correct semantics of triggerWhen" in {
      val e = EventSource[Int]
      val v = Var(7)
      val l = collectValues(v.triggerWhen(e, (x: Int, y: Int) => x + y))

      e emit 10
      v := 17
      e emit 13
      e emit 0

      l shouldEqual List(17, 30, 17)
    }

    it should "use the correct semantics of triggerWhen (2)" in {
      val ev = Var(13)
      val e = ev.toEvent
      val v = Var(7)
      val l = collectValues(v.triggerWhen(e, (x: Int, y: Int) => x + y))

      ev := 10
      v := 17
      ev := 13
      ev := 0

      l shouldEqual List(17, 30, 17)
    }

    it should "be able to use eventsource as refinement of event (i. e. there shouldn't be any issues with volatility)" in {
      trait A {
        val x: Event[Int]
        val y: Signal[Int]
      }

      object B extends A {
        val x: EventSource[Int] = ???
        val y: Var[Int] = ???
      }
    }

    it should "Never behave as neutral element" in {
      val e1 = EventSource[Int]
      val e2 = Event.Never
      val e3 = e1 merge e2

      val l1 = collectValues(e1)
      val l2 = collectValues(e2)
      val l3 = collectValues(e3)

      e1 emit 7
      e1 emit 9
      e1 emit 11

      l1 shouldEqual List(7, 9, 11)
      l2 shouldBe empty
      l3 shouldEqual List(7, 9, 11)
    }

    it should "correctly handle futures" in {
      //TODO
    }
  }

  def runPropertyTests: Unit = {
    import cats.laws.discipline._
    import reactLibrary._
    import reactLibrary.syntax._

    import language.postfixOps


    val signals: List[Var[Int]] = 0 to 3 map { _ => Var(0) } toList


    val signalGen: Gen[Signal[Int]] =
      Gen.oneOf(
        Gen.posNum[Int].map(_.pure),
        Gen.oneOf(signals)
      )

    val signalFunGen: Gen[Signal[Int => Int]] =
      Gen.oneOf[(Int, Int) => Int](
        (x: Int, y: Int) => x,
        (x: Int, y: Int) => y,
        (x: Int, y: Int) => x + y,
        (x: Int, y: Int) => 0
      ).flatMap( f =>
        signalGen.map {
          _.map(x => (y: Int) => f(x, y))
        }
      )

    implicit val arbitrarySignal = Arbitrary[reactLibrary.Signal[Int]](signalGen)
    implicit val arbitrarySignalFun = Arbitrary[Signal[Int => Int]](signalFunGen)

    import language.postfixOps

    var counter = 0

    // TODO is it allowed to do undeterministic equality here?
    // (ideally, we'd like to return Gen[Boolean])
    implicit def signalEq[A](implicit eqO: Eq[A]): algebra.Eq[Signal[A]] = new Eq[Signal[A]] {
      override def eqv(x: reactLibrary.Signal[A], y: reactLibrary.Signal[A]): Boolean = {
        if (!eqO.eqv(x.now, y.now))
          return false
        val l1 = collectValues(x)
        val l2 = collectValues(y)

        try {
          1 to 10 foreach { j =>
            signals(j % 4) := (j * 3)
            if (!eqO.eqv(x.now, y.now))
              return false
          }
          if (l1 != l2) {
            println(s"${l1} != ${l2}")
            false
          }
          else
            true
        }
        finally {
          counter += 1
          if (counter % 10 == 0) { println(s"another 10: $counter")}
          l1.ref.kill()
          l2.ref.kill()
        }
      }
    }



    implicit val intEq: Eq[Int] = new Eq[Int] {
      override def eqv(x: Int, y: Int): Boolean = x == y
    }

    implicit val intTripleEq: Eq[(Int, Int, Int)] = new Eq[(Int, Int, Int)] {
      override def eqv(x: (Int, Int, Int), y: (Int, Int, Int)): Boolean = x == y
    }


    //ApplicativeTests[Signal].applicative[Int, Int, Int].all.check
  }
}
