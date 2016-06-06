package react.impls

import cats.{FlatMap, Monad}
import react.ReactiveLibrary
import react.ReactiveLibrary._
import react.impls.helper.{DefaultEventObject, ReactiveLibraryImplementationHelper, DefaultSignalObject, NonCancelable}
import rx._
import rx.async.FutureCombinators

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

// TODO: make these unsafes more safe
import rx.Ctx.Owner.Unsafe.Unsafe

trait ReleaseObs {
  def release(o: ObsWrapper): Unit
}

class ObsWrapper(obs: Obs, releaseObs: ReleaseObs) extends Cancelable {
  def kill(): Unit = {
    obs.kill()
    releaseObs.release(this)
  }
}

//TODO: check if something like that exists in the standard library
case class CompareUnequal[+A](get: A) {
  // scalastyle:off org.scalastyle.scalariform.CovariantEqualsChecker
  override def equals(obj: scala.Any): Boolean =
    obj match {
      case x: AnyRef => eq(x)
      case _ => false
    }
  // scalastyle:on org.scalastyle.scalariform.CovariantEqualsChecker

  def map[B](f: A => B): CompareUnequal[B] = CompareUnequal(f(get))
}

trait ScalaRxImpl extends ReactiveLibrary with DefaultSignalObject with DefaultEventObject with ReactiveLibraryImplementationHelper {
  scalaRxImpl =>
  def implementationName: String = "Scala.Rx wrapper"

  // we need to hold all references to observers
  // so that they aren't garbage collected
  // (maybe this isn't necessary in js because its garbage collector isn't as powerful as jvm's)
  object ReferenceHolder extends ReleaseObs {
    val references: mutable.Set[ObsWrapper] = mutable.Set.empty

    def release(o: ObsWrapper): Unit = {
      references -= o
    }
  }

  def obsToObsWrapper(obs: Obs): ObsWrapper = {
    val result = new ObsWrapper(obs, ReferenceHolder)
    ReferenceHolder.references += result
    result
  }

  class Event[+A](private[ScalaRxImpl] val wrapped: Rx[Option[CompareUnequal[A]]]) extends EventTrait[A] {
    type F[+A] = Event[A]

    override def observe(f: A => Unit): Cancelable = {
      wrapped.triggerLater { f(wrapped.now.get.get) }
      NonCancelable
    }
  }

  implicit object eventApplicative extends EventOperationsTrait[Event] with FlatMap[Event] {
    override def merge[A](x1: Event[A], other: Event[A]): Event[A] = {
      import x1._
      val p1 = wrapped.fold((0, None): (Int, Option[CompareUnequal[A]])) { (v, current) =>
        (v._1 + 1, current)
      }
      val p2 = other.wrapped.fold((0, None): (Int, Option[CompareUnequal[A]])) { (v, current) =>
        (v._1 + 1, current)
      }

      def foldFun(v: (Int, Int, Option[CompareUnequal[A]]), current: ((Int, Option[CompareUnequal[A]]), (Int, Option[CompareUnequal[A]]))) = {
        val result =
          if (v._1 < current._1._1) {
            current._1._2
          }
          else {
            current._2._2
          }
        (current._1._1, current._2._1, result)
      }

      val result = Rx {
        (p1(), p2())
      }.fold((0, 0, None): (Int, Int, Option[CompareUnequal[A]]))(foldFun)
      new Event(result.map(_._3))
    }

    override def ap[A, B](ff: Event[(A) => B])(fa: Event[A]): Event[B] = {
      new Event(Rx {
        ff.wrapped().flatMap { x =>
          fa.wrapped().map { p =>
            val result = x.get(p.get)
            CompareUnequal(result)
          }
        }
      })
    }

    override def map[A, B](fa: Event[A])(f: (A) => B): Event[B] = {
      new Event(fa.wrapped.map {
        _.map {
          _.map(f)
        }
      })
    }

    override def filter[A](v: Event[A], f: (A) => Boolean): Event[A] = {
      new Event(v.wrapped.filter {
        case Some(x) => f(x.get)
        case None => true
      })
    }

    override def flatMap[A, B](fa: Event[A])(f: (A) => Event[B]): Event[B] = {
      def wrappedF(a: Option[CompareUnequal[A]]) = a match {
        case Some(CompareUnequal(x)) => f(x).wrapped
        case None => Rx(None)
      }

      // We have to cache the wrapped Rx
      def reduceFun(u1: (Int, Option[CompareUnequal[A]]), u2: (Int, Option[CompareUnequal[A]])): (Int, Option[CompareUnequal[A]]) =
        (u1._1 + 1, u2._2)
      var current: (Int, Rx[Option[CompareUnequal[B]]]) = (-1, Rx { None })

      def wrappedF2(a: (Int, Option[CompareUnequal[A]])) =
        if (a._1 == current._1) {
          current._2
        }
        else {
          val result = wrappedF(a._2)
          current = (a._1, result)
          result
        }

      val r = fa.wrapped.map(x => (0, x)).reduce(reduceFun).flatMap(wrappedF2).reduce { (x, y) =>
        (x, y) match {
          case (_, Some(z)) => Some(z)
          case (y, None) => y
        }
      }

      new Event(r)
    }
  }

  class Signal[+A](private[ScalaRxImpl] val wrapped: Rx[A]) extends SignalTrait[A] {
    type F[+A] = Signal[A]

    override def now: A = wrapped.now

//    override def map[B](f: (A) => B): Signal[B] =
//      new Signal(wrapped.map(f))
//
//    override def flatMap[B](f: (A) => Signal[B]): Signal[B] = {
//      def wrappedF(a: A) = f(a).wrapped
//      new Signal(wrapped.flatMap(wrappedF))
//    }
//
//    override def filter(f: A => Boolean): Signal[A] = new Signal(wrapped.filter(f))

    override def observe(f: (A) => Unit): Cancelable = {
      obsToObsWrapper(wrapped.trigger { f(wrapped.now) })
    }
  }

  implicit object signalApplicative extends SignalOperationsTrait[Signal] with FlatMap[Signal] {
    override def pure[A](x: A): Signal[A] = {
      new Signal(Rx { x })
    }

    override def ap[A, B](ff: Signal[(A) => B])(fa: Signal[A]): Signal[B] = {
      new Signal(Rx { ff.wrapped()(implicitly[Ctx.Data])(fa.wrapped()) })
    }

    override def flatMap[A, B](fa: Signal[A])(f: (A) => Signal[B]): Signal[B] = {
      def wrappedF(a: A) = f(a).wrapped
      new Signal(fa.wrapped.flatMap(wrappedF))
    }

    override def map[A, B](fa: Signal[A])(f: (A) => B): Signal[B] = {
      new Signal(fa.wrapped.map(f))
    }
  }

  override def toSignal[A](init: A, event: Event[A]): Signal[A] =
    new Signal(event.wrapped.map(_.map(_.get).getOrElse(init)))

  override def toEvent[A](signal: Signal[A]): Event[A] =
    new Event(signal.wrapped.map(x => Some(CompareUnequal(x))).fold[Option[Option[CompareUnequal[A]]]](None) {
      (x, y) =>
        x match {
          case None => Some(None)
          case Some(_) => Some(y)
        }
    }.map(_.flatten))


  override def triggerWhen[A, B, C](s: Signal[A], e: Event[B], f: (A, B) => C): Event[C] = {
    val e2 = e.wrapped.fold((0, None): (Int, Option[CompareUnequal[B]])) { (v, current) =>
      (v._1 + 1, current)
    }

    def foldFun(v: (Boolean, Int, Option[CompareUnequal[C]]), current: (A, (Int, Option[CompareUnequal[B]]))) = {
      if (v._2 < current._2._1) {
        (true, current._2._1, current._2._2.map(x => CompareUnequal(f(current._1, x.get))))
      }
      else {
        (false, current._2._1, None)
      }
    }
    val result = Rx {
      (s.wrapped(), e2())
    }.fold((false, 0, None): (Boolean, Int, Option[CompareUnequal[C]]))(foldFun)

    new Event(result.filter(_._1).map(_._3))
  }

  def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = {
    new Event(f.map(x => Some(CompareUnequal(x)): Option[CompareUnequal[A]])(ec).toRx(None)(ec, implicitly[Ctx.Owner]))
  }

  class Var[A](private val _wrapped: rx.Var[A]) extends Signal[A](_wrapped) with VarTrait[A] {
    def update(newValue: A): Unit = _wrapped.update(newValue)
  }

  object Var extends VarCompanionObject[Var] {
    override def apply[A](init: A): Var[A] = new Var(rx.Var(init))
  }

  class EventSource[A](_wrapped: rx.Var[Option[CompareUnequal[A]]]) extends Event[A](_wrapped) with EventSourceTrait[A] {
    def emit(value: A): Unit = _wrapped.update(Some(CompareUnequal(value)))
  }

  object EventSource extends EventSourceCompanionObject[Event, EventSource] {
    def apply[A](): EventSource[A] = new EventSource(rx.Var(None))
  }

  object unsafeImplicits extends UnsafeImplicits {
    implicit val eventApplicative: EventOperationsTrait[Event] with FlatMap[Event] = scalaRxImpl.eventApplicative
    implicit val signalApplicative: SignalOperationsTrait[Signal] with FlatMap[Signal] = scalaRxImpl.signalApplicative
  }
}
