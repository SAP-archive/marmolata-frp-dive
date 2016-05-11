package react.impls

import react.ReactiveLibrary
import react.ReactiveLibrary._
import rx._

// TODO: make these unsafes more safe
import rx.Ctx.Owner.Unsafe.Unsafe

object ReactRxImpl extends ReactiveLibrary {
  def implementationName = "ReactRxImpl"

  class EventSource[+A](private[ReactRxImpl] val wrapped: Rx[Option[A]]) extends (Monadic[A]) with Observer[A] with Filterable[EventSource, A] {
    type F[+A] = EventSource[A]

    override def map[B](f: A => B): EventSource[B] = {
      new EventSource(wrapped.map(_.map(f)))
    }

    override def flatMap[B](f: (A) => EventSource[B]): EventSource[B] = {
      def wrappedF(a: Option[A]) = a match {
        case Some(x) => f(x).wrapped
        case None => Rx(None)
      }
      new EventSource(wrapped.flatMap(wrappedF))
    }

    override def observe(f: A => Unit): Unit = {
      wrapped.triggerLater { f(wrapped.now.get) }
    }

    override def filter(f: (A) => Boolean): EventSource[A] = {
      new EventSource(wrapped.filter{
        case Some(x) => f(x)
        case None => true
      })
    }
  }

  class Signal[+A](private[ReactRxImpl] val wrapped: Rx[A]) extends Monadic[A] with Filterable[Signal, A] with SignalTrait[A] with Observer[A] {
    type F[+A] = Signal[A]

    override def now: A = wrapped.now

    override def map[B](f: (A) => B): Signal[B] =
      new Signal(wrapped.map(f))

    override def flatMap[B](f: (A) => Signal[B]): Signal[B] = {
      def wrappedF(a: A) = f(a).wrapped
      new Signal(wrapped.flatMap(wrappedF))
    }

    override def filter(f: (A) => Boolean): Signal[A] = new Signal(wrapped.filter(f))

    override def observe(f: (A) => Unit): Unit = wrapped.trigger { f(wrapped.now) }
  }

  override def toSignal[A](init: A, event: ReactRxImpl.EventSource[A]): Signal[A] =
    new Signal(event.wrapped.map(_.getOrElse(init)))

  override def toEvent[A](signal: Signal[A]): EventSource[A] =
    new EventSource(signal.wrapped.map(Some(_)))

  class Var[A](private val _wrapped: rx.Var[A]) extends Signal[A](_wrapped) with VarTrait[A] {
    def update(newValue: A): Unit = _wrapped.update(newValue)
  }

  object Var extends VarCompanionObject[Var] {
    override def apply[A](init: A): Var[A] = new Var(rx.Var(init))
  }
}
