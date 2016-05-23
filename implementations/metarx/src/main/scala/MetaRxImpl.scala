package react.impls

import cats.Monad
import pl.metastack.metarx
import pl.metastack.metarx.{Cancelable => MetaCancelable, _}
import react.impls.helper.{DefaultConstObject, NonCancelable}
import react.ReactiveLibrary
import _root_.react.ReactiveLibrary._

import scala.concurrent.{ExecutionContext, Future}

trait MetaRxImpl extends ReactiveLibrary with DefaultConstObject {
  metaRxImpl =>

  def implementationName = "MetaRxImpl"

  case class Cancelable(wrapped: ReadChannel[Unit]) extends ReactiveLibrary.Cancelable {
    override def kill(): Unit = wrapped.dispose()
  }

  class EventSourceImpl[+A, D <: A](private[MetaRxImpl] val wrapped: ReadChannel[D]) extends EventTrait[A] {
    type F[+X] = EventSourceImpl[X, _ <: X]

    def map[B](f: A => B): EventSourceImpl[B, B] =
      new EventSourceImpl(wrapped.map(f))

    def flatMap[B](f: A => F[B]): F[B] = {
      def wrappedF(a: A): ReadChannel[B] = f(a).wrapped.map(x => x)
      new EventSourceImpl(wrapped.flatMap(wrappedF))
    }

    override def observe(f: A => Unit): Cancelable = {
      Cancelable(wrapped.silentAttach(f))
    }

//    override def filter(f: (A) => Boolean): EventSourceImpl[A, D] = {
//      new EventSourceImpl(wrapped.filter(f))
//    }
//
//    override def zip[B](other: EventSourceImpl[B, _ <: B]): EventSourceImpl[(A, B), _ <: (A, B)] = {
//      new EventSourceImpl(wrapped.zip(other.wrapped.map(x => x)))
//    }
//
//    def merge[B >: A](other: EventSourceImpl[B, _ <: B]): EventSourceImpl[B, B] = {
//      new EventSourceImpl(wrapped.map(x => x: B).merge(other.wrapped.map(x => x: B)))
//    }
  }

  type Event[+A] = EventSourceImpl[A, _ <: A]

  class SignalImpl[+A, D <: A](private[MetaRxImpl] val wrapped: ReadStateChannel[D]) extends SignalTrait[A] {
    type F[+B] = SignalImpl[B, _ <: B]

//    override def map[B](f: A => B): SignalImpl[B, B] = {
//      new SignalImpl(wrapped.map(f).distinct.cache(f(wrapped.get)))
//    }
//
//    override def flatMap[B](f: A => F[B]): F[B] = {
//      def wrappedF(a: A): ReadChannel[B] = f(a).wrapped.map(x => x)
//      new SignalImpl(wrapped.flatMap(wrappedF).distinct.cache(f(wrapped.get).wrapped.get))
//    }

    def now: A = wrapped.get

    def observe(f: A => Unit): Cancelable = {
      Cancelable(wrapped.attach(f))
    }
  }

  type Signal[+A] = SignalImpl[A, _ <: A]

  object signalApplicative extends SignalOperationsTrait[Signal] with Monad[Signal] {
    def pure[A](x: A): SignalImpl[A, _ <: A] = {
      new SignalImpl(metarx.Var(x))
    }

    override def ap[A, B](ff: SignalImpl[(A) => B, _ <: (A) => B])(fa: SignalImpl[A, _ <: A]): SignalImpl[B, _ <: B] = {
      new SignalImpl(ff.wrapped.zip(fa.wrapped).map { case (a, b) => a(b) }.distinct.cache(ff.wrapped.get(fa.wrapped.get)))
    }

    override def flatMap[A, B](fa: SignalImpl[A, _ <: A])(f: (A) => SignalImpl[B, _ <: B]): SignalImpl[B, _ <: B] = {
      def wrappedF(a: A): ReadChannel[B] = f(a).wrapped.map(x => x)
      new SignalImpl(fa.wrapped.flatMap(wrappedF).distinct.cache(f(fa.wrapped.get).wrapped.get))
    }
  }

  def _toSignal[A](init: A, event: EventSourceImpl[A, X] forSome { type X <: A}): Signal[A] =
    new SignalImpl(event.wrapped.map(x => x: A).cache(init))

  def toSignal[A](init: A, event: Event[A]): Signal[A] = _toSignal[A](init, event)

  def toEvent[A](signal: Signal[A]): Event[A] =
    new EventSourceImpl(signal.wrapped)

  def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = {
    new EventSourceImpl(FutureToReadChannel(f))
  }

  object eventApplicative extends EventOperationsTrait[Event] with Monad[Event] {
    override def pure[A](x: A): EventSourceImpl[A, _ <: A] = {
      val ch = Channel[A]()
      ch := x
      new EventSourceImpl(ch)
    }

    def merge[A](x1: EventSourceImpl[A, _ <: A], x2: EventSourceImpl[A, _ <: A]): EventSourceImpl[A, _ <: A] =
      new EventSourceImpl(x1.wrapped.map(x => x: A) merge x2.wrapped.map(x => x: A))

    override def ap[A, B](ff: EventSourceImpl[(A) => B, _ <: (A) => B])(fa: EventSourceImpl[A, _ <: A]): EventSourceImpl[B, _ <: B] =
      new EventSourceImpl(ff.wrapped zip fa.wrapped map { case (a, b) => a(b) })

    override def filter[A](v: EventSourceImpl[A, _ <: A], cond: (A) => Boolean): EventSourceImpl[A, _ <: A] =
      new EventSourceImpl(v.wrapped.filter(cond))

    override def flatMap[A, B](fa: EventSourceImpl[A, _ <: A])(f: (A) => EventSourceImpl[B, _ <: B]): EventSourceImpl[B, _ <: B] = {
      def wrappedF(a: A): ReadChannel[B] = f(a).wrapped.map(x => x)
      new EventSourceImpl(fa.wrapped.flatMap(wrappedF))
    }
  }

  class Var[A](private val _wrapped: metarx.Var[A]) extends SignalImpl[A, A](_wrapped.distinct.cache(_wrapped.get)) with VarTrait[A] {
    override def update(newValue: A): Unit = {
      _wrapped := newValue
    }

    override def now: A = wrapped.get
  }

  object Var extends VarCompanionObject[Var] {
    override def apply[A](init: A): Var[A] = new Var(metarx.Var(init))
  }

  class EventSource[A](_wrapped: metarx.Channel[A]) extends EventSourceImpl[A, A](_wrapped) with EventSourceTrait[A] {
    def emit(value: A): Unit = _wrapped := value
  }

  object Event extends EventCompanionObject[EventSource] {
    def apply[A](): EventSource[A] = new EventSource(metarx.Channel())
  }

  object unsafeImplicits extends UnsafeImplicits {
    override implicit val eventApplicative = metaRxImpl.eventApplicative
    override implicit val signalApplicative = metaRxImpl.signalApplicative
  }
}