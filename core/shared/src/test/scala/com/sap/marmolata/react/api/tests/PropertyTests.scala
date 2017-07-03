package com.sap.marmolata.react.api.tests

import cats.Eq
import cats.laws.discipline.{FunctorTests, MonadTests}
import org.scalacheck.{Arbitrary, Test, Gen}
import org.scalatest.{FlatSpec, Matchers}
import com.sap.marmolata.react.api.ReactiveLibrary.Annotation
import cats.instances.tuple._
import cats.instances.int._

import language.postfixOps

trait PropertyTests extends ReactiveLibraryTests {
  import reactiveLibrary._
  import reactiveLibrary.syntax._

  case class VarTag(i: Int) extends Annotation {
    override def description: String = s"Var($i)"
  }

  case class EventSourceTag(i: Int) extends Annotation {
    override def description: String = s"EventSource($i)"
  }

  case class ConstTag(i: Int) extends Annotation {
    override def description: String = s"Const($i)"
  }

  val signals: List[Var[Int]] = 0 to 3 map { i => Var(0).tag(VarTag(i)) } toList
  val events: List[EventSource[Int]] = 0 to 3 map { i => EventSource[Int].tag(EventSourceTag(i)) } toList


  val signalGen: Gen[Signal[Int]] =
    Gen.oneOf(
      Gen.posNum[Int].map(z => (z.pure: Signal[Int]).tag(ConstTag(z))),
      Gen.oneOf(signals)
    )

  val signalFunGen: Gen[Signal[Int => Int]] =
    Gen.oneOf[((Int, Int) => Int, String)](
      ((x: Int, y: Int) => x, "left"),
      ((x: Int, y: Int) => y, "right"),
      ((x: Int, y: Int) => x + y, "plus"),
      ((x: Int, y: Int) => 0, "const0")
    ).flatMap { case (f, descr) =>
      signalGen.map {
        z => z.map(x => (y: Int) => f(x, y))
      }
    }

  implicit val arbitrarySignal = Arbitrary[Signal[Int]](signalGen)
  implicit val arbitrarySignalFun = Arbitrary[Signal[Int => Int]](signalFunGen)

  lazy val eventGen: Gen[Event[Int]] =
    Gen.frequency(
      (10, Gen.oneOf[Event[Int]](events)),
      (3, Gen.const(Event.Never))
      //,(3, Gen.lzy(eventGen).flatMap(x => Gen.lzy(eventGen).map(x.merge(_)))),
      //(1, Gen.lzy(eventGen).map(_.map(_ + 10)))
    )

  val eventFunGen: Gen[Event[Int => Int]] =
    Gen.oneOf[((Int, Int) => Int, String)](
      ((x: Int, y: Int) => x, "left"),
      ((x: Int, y: Int) => y, "right"),
      ((x: Int, y: Int) => x + y, "plus"),
      ((x: Int, y: Int) => 0, "const0")
    ).flatMap { case (f, descr) =>
      eventGen.map {
        z => z.map(x => (y: Int) => f(x, y))
      }
    }

  implicit val arbitraryEvent = Arbitrary[Event[Int]](eventGen)
  implicit val arbitaryEventFun = Arbitrary[Event[Int => Int]](eventFunGen)

  import language.postfixOps

  // TODO is it allowed to do undeterministic equality here?
  // (ideally, we'd like to return Gen[Boolean])
  // see also https://github.com/typelevel/cats/issues/1058
  implicit def signalEq[A](implicit eqO: Eq[A]): cats.Eq[Signal[A]] = new Eq[Signal[A]] {
    override def eqv(x: Signal[A], y: Signal[A]): Boolean = {
      if (!eqO.eqv(x.now, y.now)) {
        info(s"${x.now} != ${y.now} [values: ${signals.map(_.now)}]")
        return false
      }
      val l1 = collectValues(x)
      val l2 = collectValues(y)

      try {
        1 to 10 foreach { j =>
          signals(j % 4) := (j * 3)
          if (!eqO.eqv(x.now, y.now)) {
            info(s"${x.now} != ${y.now} [values: ${signals.map(_.now)}]")
            return false
          }
        }
        if (l1 != l2) {
          info(s"${l1} != ${l2}")
          false
        }
        else
          true
      }
      finally {
        l1.ref.kill()
        l2.ref.kill()
      }
    }
  }

  implicit def eventEq[A](implicit eqO: Eq[A]): cats.Eq[Event[A]] = new Eq[Event[A]] {
    override def eqv(x: Event[A], y: Event[A]): Boolean = {
      val l1 = collectValues(x)
      val l2 = collectValues(y)

      try {
        1 to 10 foreach { j =>
          events(j % 4) emit (j * 3)
          if (!(l1.length == l2.length && (l1 zip l2 forall { case (x, y) => eqO.eqv(x, y) }))) {
            info(s"${l1} != ${l2}")
            return false
          }
        }
        true
      }
      finally {
        l1.ref.kill()
        l2.ref.kill()
      }
    }
  }

  behavior of "Signal"
  MonadTests[Signal](unsafeImplicits.marmolataDiveSignalTypeclass).monad[Int, Int, Int].all.properties.foreach {
    case (name, property) =>
      it should name in {
        val test = Test.check(property) {
          _.withMinSuccessfulTests(100)
        }
        info(org.scalacheck.util.Pretty.pretty(test))
        assert(test.passed)
      }
  }

  behavior of "Event"
  FunctorTests[Event].functor[Int, Int, Int].all.properties.foreach {
    case (name, property) =>
      it should name in {
        val test = Test.check(property) {
          _.withMinSuccessfulTests(100)
        }
        info(org.scalacheck.util.Pretty.pretty(test))
        assert(test.passed)
      }
  }
}
