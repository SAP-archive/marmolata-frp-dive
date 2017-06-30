package com.sap.marmolata.react.impls.scalarx

import cats.{FlatMap, Monad}
import com.sap.marmolata.react.api.ReactiveLibrary
import com.sap.marmolata.react.react.core.ReactiveLibrary._
import com.sap.marmolata.react.react.impls.helper.{NonCancelable, ReactiveLibraryImplementationHelper, _}
import rx._
import rx.async.FutureCombinators

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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

trait ScalaRxImpl extends ReactiveLibrary
  with DefaultReassignableVar with DefaultEventObject
  with ReactiveLibraryImplementationHelper {
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

  case class TrackDependency private[ScalaRxImpl](u: Ctx.Data) extends TrackDependencyTrait

  override object Signal extends SignalCompanionObject[Signal, TrackDependency] {
    override def Const[A](value: A): Signal[A] =
      new Signal(Rx { value })

    override def apply[A](fun: (TrackDependency) => A): Signal[A] = {
      new Signal(Rx.build((owner, data) => fun(TrackDependency(data))))
    }

    override def breakPotentiallyLongComputation()(implicit td: TrackDependency): Unit = {}
  }

  class Event[+A](private[ScalaRxImpl] val wrapped: Rx[Option[CompareUnequal[A]]]) extends EventTrait[A] {
    type F[+A] = Event[A]

    override def observe(f: A => Unit): Cancelable = {
      wrapped.triggerLater { f(wrapped.now.get.get) }
      NonCancelable
    }
  }

  implicit object marmolataDiveEventTypeclass extends EventOperationsTrait[Event] {
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
  }

  class Signal[+A](private[ScalaRxImpl] val wrapped: Rx[A]) extends SignalTrait[A, TrackDependency] {
    type F[+A] = Signal[A]

    override def now: A = wrapped.now

    override def observe(f: (A) => Unit): Cancelable = {
      obsToObsWrapper(wrapped.trigger { f(wrapped.now) })
    }

    override def apply()(implicit trackDependency: TrackDependency): A = wrapped()(trackDependency.u)
  }

  implicit object marmolataDiveSignalTypeclass extends SignalOperationsTrait[Signal] with Monad[Signal] {
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

    //TODO: make this tail recursive
    override def tailRecM[A, B](a: A)(f: (A) => Signal[Either[A, B]]): Signal[B] = {
      flatMap(f(a)) {
        case Left(b) => tailRecM(b)(f)
        case Right(b) => pure(b)
      }
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


  override protected[react] def flattenEvents[A](s: Signal[Event[A]]): Event[A] = {
    val eventRx = s.wrapped.fold[(Int, Event[A])](0, s.now)((state, ev) => (state._1 + 1, ev))

    val ev = Rx {
      val a = eventRx()
      (a._1, a._2.wrapped())
    }.fold[(Int, Int, Option[CompareUnequal[A]])]((0, 0, None)) { (state, next) =>
      if (state._1 == next._1)
        (state._1, state._2 + 1, next._2)
      else
        (next._1, 0, next._2)
    }

    val ev2 = ev.fold[Option[CompareUnequal[A]]](None) {
      (state, next) =>
        // TODO: figure out somehow if the event changed at the same point in time
        // for now, do not trigger an event in the case the signal just changed
        if (next._2 == 0)
          state
        else
          next._3
    }

    new Event(ev2)
  }

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

  override protected[react] def fold[A, B](e: Event[A], init: B, fun: (A, B) => B): Signal[B] = {
    new Signal(e.wrapped.fold(init) { (current, event) =>
      event.map(x => fun(x.get, current)).getOrElse(current)
    })
  }


  override protected[react] def signalToTry[A](from: Signal[A]): Signal[Try[A]] = {
    new Signal(
      Rx {
        Try(from.wrapped())
      }
    )
  }

  def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = {
    new Event(f.map(x => Some(CompareUnequal(x)): Option[CompareUnequal[A]])(ec).toRx(None)(ec, implicitly[Ctx.Owner]))
  }

  class Var[A](private val _wrapped: rx.Var[A]) extends Signal[A](_wrapped) with VarTrait[A, TrackDependency] {
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
    implicit val marmolataDiveSignalTypeclass: SignalOperationsTrait[Signal] with Monad[Signal] = scalaRxImpl.marmolataDiveSignalTypeclass
  }
}
