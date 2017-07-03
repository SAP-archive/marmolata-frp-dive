package com.sap.marmolata.react.api.tests

import org.scalatest.{Succeeded, FlatSpec, Matchers}
import com.sap.marmolata.react.api.ReactiveLibrary.Annotation

import scala.collection.mutable
import scala.concurrent.Promise
import scala.ref.WeakReference
import scala.util.{Failure, Success}

trait DefaultTests extends ReactiveLibraryTests {
  import reactiveLibrary._
  import reactiveLibrary.syntax._

  behavior of s"ReactiveLibrary ${implementationName}"

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
    pendingFor(MetaRx) {
      import unsafeImplicits.marmolataDiveSignalTypeclass
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

    v1.map(v2.update(_)).observe { _ => {} }

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
    val v = DeprecationForwarder.C.futureToEvent(p.future)
    val l = collectValues(v)
    queue.runQueue()

    p success 10
    queue.runQueue()

    l shouldEqual List(10)
  }

  it should "handle futures which come in out of order" in {
    implicit val queue = new SimpleExecutionContext()

    val promises = new Array[Promise[Int]](10)
    (0 to 9) foreach { i => promises(i) = Promise[Int]() }
    val v = Var(0)
    val w = v.toEvents { i =>
      DeprecationForwarder.C.futureToEvent(promises(i).future)
    }

    val l = collectValues(w)

    queue.runQueue()
    v.update(1)
    queue.runQueue()

    v.update(2)
    queue.runQueue()

    v.update(3)
    queue.runQueue()

    promises(1) success 1
    queue.runQueue()


    promises(3) success 3
    queue.runQueue()


    promises(2) success 2
    queue.runQueue()

    v.update(4)
    promises(4) success 4

    queue.runQueue()

    l shouldEqual List(3, 4)
  }

  it should "not trigger this strange flatMap bug" in {
    implicit val queue = new SimpleExecutionContext()
    val v = EventSource[Int]
    val p = Promise[Int]
    val r = v.toSignal.toEvents { i => DeprecationForwarder.C.futureToEvent(p.future) }
    v emit 10
    queue.runQueue()
    val l = collectValues(r)
    p success 7
    queue.runQueue()

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
    pendingFor(MetaRx) {
      val v = Var(0)
      val w = v.map { x => throw new Exception("Hi") }
      v.update(7)
      intercept[Exception] {
        w.now
      }
      Succeeded
    }
  }

  // would be nice to have!
  it should "allow exceptions 2" in {
    val v = Var(0)
    val w = v.map { x => if (x == 7) throw new Exception("Hi") }

    try {
      w.observe(_ => Unit)
    }
    catch {
      case _: Exception =>
    }

    try {
      v := 7
    } catch {
      case _: Exception => pending
    }
  }

  it should "compute map lazily" in {
    pendingFor(ScalaRx, MetaRx) {
      val v = Var(0)
      var counter = 0
      val w = v.map { x => counter += 1; x }
      v.update(7)

      counter shouldBe 0
    }
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
    pendingFor(ScalaRx, MetaRx) {
      val v = Var(0)
      val w = v.map(_ + 1)
      var counter = 0

      import unsafeImplicits.marmolataDiveSignalTypeclass

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
  }

  it should "not remember its value as an event (2)" in {
    pendingFor(MetaRx) {
      val v = Var(7)
      v.toEvent.toSignal(0).now shouldBe 0
    }
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
    pendingFor(ScalaRx) {
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

  it should "correctly handle changeWhen" in {
    val v = Var(0)
    val e = EventSource[Unit]
    val l = collectValues(v.changeWhen(e))

    v := 1
    v := 2
    e emit Unit
    e emit Unit

    v := 7
    e emit Unit

    v := 10

    l shouldBe List(0, 2, 7)
  }

  it should "correctly handle futures" in {
    //TODO
  }

  it should "support stack traces" in {
    def f(): Unit =
      info(new RuntimeException().getStackTrace().map(x => x.toString).reduce(_ + "\n" + _))
    f()
  }

  it should "play well with ReassignableSignal" in {
    case class Input(value: ReassignableVar[String] = ReassignableVar("hallo"), visible: ReassignableVar[Boolean] = ReassignableVar(false))

    val valueVar = Var("Marmolata!")
    val visibleVar = Var(true)
    val input = Input()
    input.value.subscribe(valueVar)
    input.visible.subscribe(visibleVar)
    assert(input.visible.now == true)
    visibleVar := false
    assert(input.visible.now == false)
    assert(input.value.now == "Marmolata!")
    valueVar := "Rein in die Monade"
    assert(input.value.now == "Rein in die Monade")
  }

  it should "work well with Event.toSignal" in {
    pendingFor(ScalaRx) {
      val e = EventSource[Int]
      e emit 5
      e emit 13
      val s = e.toSignal(27)
      s.now shouldBe 27
      e emit 77
      s.now shouldBe 77

      val s2 = e.toSignal(33)
      s.now shouldBe 77
      s2.now shouldBe 33

      e emit 100
      s.now shouldBe 100
      s2.now shouldBe 100
    }
  }

  it should "work with double-ap" in {
    val v = Var(1)

    val e = v.map((x: Int) => (y: Int) => (z: Int) => x + y + z).ap(v).ap(v)
    val l = collectValues(e)

    v := 2
    v := 3
    v := 4

    l shouldBe List(3, 6, 9, 12)
  }

  it should "behave well with flatMap" in {
    import unsafeImplicits.marmolataDiveSignalTypeclass
    val v = Var(1)
    val l = collectValues(Signal.Const(identity[Int] _).flatMap(f => v.map(f)))

    v := 2
    v := 3
    v := 4

    l shouldBe List(1, 2, 3, 4)
  }

  it should "support fold" in {
    val e = EventSource[Unit]
    val count = e.fold(0){ (x, y) => y + 1 }
    val l = collectValues(count)

    1 to 10 foreach { _ => e emit Unit }

    l shouldBe List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  }

  it should "support fold (2): events before the fold call don't change the result" in {
    pendingFor(ScalaRx) {
      val e = EventSource[Unit]
      e emit Unit

      val count = e.fold(0) { (x, y) => y + 1 }
      val l = collectValues(count)

      1 to 10 foreach { _ => e emit Unit }

      l shouldBe List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    }
  }

  it should "support fold (3)" in {
    pendingFor(ScalaRx) {
      val e = EventSource[Int]

      e emit 1

      val sum = e.fold(0)(_ + _)
      val l = collectValues(sum)
      1 to 5 foreach {
        e emit _
      }

      l shouldBe List(0, 1, 3, 6, 10, 15)
    }
  }

  it should "allow tagging" in {
    object Tag1 extends Annotation {
      override def description: String = ""
    }

    object Tag2 extends Annotation {
      override def description: String = ""
      override def parent: Option[Annotation] = Some(Tag1)
    }

    val v = Var(7).tag(Tag2)
    v.containsTag(Tag1) shouldBe true
    v.containsTag(Tag2) shouldBe true
    v.allAnnotations.should(contain).theSameElementsAs(Seq(Tag2))
  }

  it should "support Signal { ... } syntax (1)" in {
    val v = Var(0)
    val w = Signal { implicit s => v() + v() }
    val l = collectValues(w)

    v := 3
    v := 10
    v := 7

    l shouldBe List(0, 6, 20, 14)
  }

  it should "support Signal { ... } syntax (2)" in {
    val v = Var(false)
    val w = Var(7)
    val q = Var(10)

    val l = collectValues(Signal { implicit i => if (v()) w() else q() })

    v := true
    w := 10
    q := 20
    v := false
    w := 30

    l shouldBe List(10, 7, 10, 20)
  }

  it should "support map inside Signal { ... } " in {
    var recalculateCount = 0
    def recalc(): Unit = {
      recalculateCount += 1
      if (recalculateCount == 100) {
        fail("recalculated too often")
      }
    }
    val v = Var(0)
    val r = Signal { implicit i => v.map(_ + 1)() + 1 }
    val l = collectValues(r)

    v := 3
    l shouldBe List(2, 5)
  }

  it should "not go into an endless loop when in inconsistent state" in {
    pendingFor(ScalaRx) {
      val v1 = Var(0)
      val v2 = v1.map(identity)
      val v3 = v2.map(identity).map(identity).map(identity)
      var fail = false
      v3.observe { _ => {} }
      val v = Signal { implicit i =>
        if (v1() == 0) {
          v2()
        }
        else {
          val b = v3() != v2()
          Signal.breakPotentiallyLongComputation()
          if (b) {
            val t = System.currentTimeMillis()
            while (t + 2000 < System.currentTimeMillis()) {}
            fail = true
            103
          }
          else {
            17
          }
        }
      }
      val l = collectValues(v)
      v1 := 2
      v1 := 3
      l shouldBe Seq(0, 17)
      fail shouldBe false
    }
  }

  it should "not throw an exception from inconsistent state" in {
    val v = Var(0)
    val w = v.map(identity).map(identity)
    w.observe { _ => {} }
    val l = collectValues(Signal { implicit i =>
      if (v() == 0) 13 else if (v() != w()) throw new Exception("I should have been catched") else 23
    })

    v := 1

    l shouldBe Seq(13, 23)
  }

  it should "support Signal inside Signal" in {
    pending
  }

  it should "support flattenEvents" in {
    val e1 = EventSource[Int]
    val e2 = EventSource[Int]

    val v = Var[Event[Int]](e1)

    val l = collectValues(v.flatten)

    e1 emit 10
    e2 emit 12
    e1 emit 13

    v := e2

    e1 emit 14
    e2 emit 15
    e2 emit 15

    v := e1

    e1 emit 10
    e2 emit 10

    v := Event.Never

    e1 emit 12
    e2 emit 13

    l shouldBe List(10, 13, 15, 15, 10)
  }

  it should "trigger event of flattenEvents when Signal changes at the same time" in {
    pendingFor(ScalaRx) {
      val v = Var(0)

      val e1 = v.map(_ * 2).toEvent
      val e2 = v.map(_ * 3).toEvent

      val s = v.map { w =>
        if (w % 2 == 0)
          e1
        else
          e2
      }

      val e = s.flatten
      val l = collectValues(e)

      v := 2
      v := 4
      v := 3
      v := 7
      v := 8

      l shouldBe List(4, 8, 9, 21, 16)
    }
  }

  "toTry" should "catch exceptions" in {
    pendingFor(SelfRx, MetaRx) {
    val v = Var(0)

    val ex1 = new Exception("Hallo")
    val ex2 = new Exception("Hallo2")

    val w = v.map {
      case 0 => throw ex1
      case 1 => throw ex2
      case x => x
    }

    val l = collectValues(w.toTry)

    v := 10
    v := 1
    v := 0

    l shouldBe Seq(Failure(ex1), Success(10), Failure(ex2), Failure(ex1))
    }
  }
}
