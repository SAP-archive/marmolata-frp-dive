package react.impls

import pl.metastack.metarx
import pl.metastack.metarx.{Cancelable => MetaCancelable, _}
import react.impls.helper.NonCancelable
import react.ReactiveLibrary
import _root_.react.ReactiveLibrary._

object MetaRxImpl extends ReactiveLibrary  {
  def implementationName = "MetaRxImpl"

  class EventSourceImpl[+A, D <: A](private[MetaRxImpl] val wrapped: ReadChannel[D]) extends Monadic[A] with Observable[A] with Filterable[Event, A] {
    type F[+X] = EventSourceImpl[X, _ <: X]

    def map[B](f: A => B): EventSourceImpl[B, B] =
      new EventSourceImpl(wrapped.map(f))

    def flatMap[B](f: A => F[B]): F[B] = {
      def wrappedF(a: A): ReadChannel[B] = f(a).wrapped.map(x => x)
      new EventSourceImpl(wrapped.flatMap(wrappedF))
    }

    override def observe(f: A => Unit): Cancelable = {
      wrapped.silentAttach(f)
      NonCancelable
    }

    override def filter(f: (A) => Boolean): EventSourceImpl[A, D] = {
      new EventSourceImpl(wrapped.filter(f))
    }

    override def zip[B](other: EventSourceImpl[B, _ <: B]): EventSourceImpl[(A, B), _ <: (A, B)] = {
      new EventSourceImpl(wrapped.zip(other.wrapped.map(x => x)))
    }
  }

  type Event[+A] = EventSourceImpl[A, _ <: A]

  class SignalImpl[+A, D <: A](private[MetaRxImpl] val wrapped: ReadStateChannel[D]) extends Monadic[A] with SignalTrait[A] with Observable[A] {
    type F[+B] = SignalImpl[B, _ <: B]

    override def map[B](f: A => B): SignalImpl[B, B] = {
      new SignalImpl(wrapped.map(f).cache(f(wrapped.get)))
    }

    override def flatMap[B](f: A => F[B]): F[B] = {
      def wrappedF(a: A): ReadChannel[B] = f(a).wrapped.map(x => x)
      new SignalImpl(wrapped.flatMap(wrappedF).cache(f(wrapped.get).wrapped.get))
    }

    def now: A = wrapped.get

    def observe(f: A => Unit): Cancelable = {
      wrapped.attach(f)
      NonCancelable
    }
  }

  type Signal[+A] = SignalImpl[A, _ <: A]

  def _toSignal[A](init: A, event: EventSourceImpl[A, X] forSome { type X <: A}): Signal[A] =
    new SignalImpl(event.wrapped.map(x => x: A).cache(init))

  def toSignal[A](init: A, event: Event[A]): Signal[A] = _toSignal[A](init, event)

  def toEvent[A](signal: Signal[A]): Event[A] =
    new EventSourceImpl(signal.wrapped)

  class Var[A](private val _wrapped: metarx.Var[A]) extends SignalImpl[A, A](_wrapped) with VarTrait[A] {
    override def update(newValue: A): Unit = {
      _wrapped := newValue
    }

    override def now: A = wrapped.get
  }

  object Var extends VarCompanionObject[MetaRxImpl.Var] {
    override def apply[A](init: A): Var[A] = new Var(metarx.Var(init))
  }
}