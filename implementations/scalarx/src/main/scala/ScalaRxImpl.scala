package react.impls

import com.sun.javafx.collections.ObservableListWrapper
import react.ReactiveLibrary
import react.ReactiveLibrary._
import react.impls.helper.NonCancelable
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
  override def equals(obj: scala.Any): Boolean = false

  def map[B](f: A => B) = CompareUnequal(f(get))
}

trait ScalaRxImpl extends ReactiveLibrary {
  def implementationName = "Scala.Rx wrapper"

  // we need to hold all references to observers
  // so that they aren't garbage collected
  object ReferenceHolder extends ReleaseObs {
    val references: mutable.Set[ObsWrapper] = mutable.Set.empty

    def release(o: ObsWrapper): Unit = {
      references -= o
    }
  }


  implicit def obsToObsWrapper(obs: Obs): ObsWrapper = {
    val result = new ObsWrapper(obs, ReferenceHolder)
    ReferenceHolder.references += result
    result
  }

  class Event[+A](private[ScalaRxImpl] val wrapped: Rx[Option[CompareUnequal[A]]]) extends (Monadic[A]) with Observable[A] with Filterable[Event, A] {
    type F[+A] = Event[A]

    override def map[B](f: A => B): Event[B] = {
      new Event(wrapped.map(_.map(_.map(f))))
    }

    def flatMap[B](f: A => Event[B]): Event[B] = {
      def wrappedF(a: Option[CompareUnequal[A]]) = a match {
        case Some(CompareUnequal(x)) => f(x).wrapped
        case None => Rx(None)
      }
      new Event(wrapped.flatMap(wrappedF).reduce { (x, y) =>
        (x, y) match {
          case (_, Some(z)) => Some(z)
          case (y, None) => y
        }
      })
    }

    def flatMapWhichAcceptsLateEvents[B](f: (A) => Event[B]): Event[B] = {
      val withLast = wrapped.fold(List.empty[Option[Rx[Option[CompareUnequal[B]]]]]){ (list, next) => (next.map((z: CompareUnequal[A]) => f(z.get).wrapped) +: list) }

      val result = Rx {
        withLast().collectFirst {
          case Some(rx) if (rx().isDefined) =>
            rx().get
        }
      }

      new Event(result)
    }

    override def observe(f: A => Unit): Cancelable = {
      wrapped.triggerLater { f(wrapped.now.get.get) }
      NonCancelable
    }

    override def filter(f: (A) => Boolean): Event[A] = {
      new Event(wrapped.filter{
        case Some(x) => f(x.get)
        case None => true
      })
    }
  }

  class Signal[+A](private[ScalaRxImpl] val wrapped: Rx[A]) extends Monadic[A] with Filterable[Signal, A] with SignalTrait[A] with Observable[A] {
    type F[+A] = Signal[A]

    override def now: A = wrapped.now

    override def map[B](f: (A) => B): Signal[B] =
      new Signal(wrapped.map(f))

    override def flatMap[B](f: (A) => Signal[B]): Signal[B] = {
      def wrappedF(a: A) = f(a).wrapped
      new Signal(wrapped.flatMap(wrappedF))
    }

    override def filter(f: A => Boolean): Signal[A] = new Signal(wrapped.filter(f))

    override def observe(f: (A) => Unit): Cancelable = {
      wrapped.trigger { f(wrapped.now) }
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


  def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = {
    new Event(f.map(x => Some(CompareUnequal(x)): Option[CompareUnequal[A]]).toRx(None))
  }

  class Var[A](private val _wrapped: rx.Var[A]) extends Signal[A](_wrapped) with VarTrait[A] {
    def update(newValue: A): Unit = _wrapped.update(newValue)
  }

  object Var extends VarCompanionObject[Var] {
    override def apply[A](init: A): Var[A] = new Var(rx.Var(init))
  }

  class EventSource[A](_wrapped: rx.Var[Option[CompareUnequal[A]]]) extends Event[A](_wrapped) with NativeEventTrait[A] {
    def emit(value: A): Unit = _wrapped.update(Some(CompareUnequal(value)))
  }

  object Event extends EventCompanionObject[EventSource] {
    def apply[A](): EventSource[A] = new EventSource(rx.Var(None))
  }
}
