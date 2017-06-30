package com.sap.marmolata.react.react.impls.metarx

import cats.{FlatMap, Monad}
import com.sap.marmolata.react.api.ReactiveLibrary
import pl.metastack.metarx
import pl.metastack.metarx.{Cancelable => MetaCancelable, _}
import com.sap.marmolata.react.react.core.ReactiveLibrary._
import com.sap.marmolata.react.react.impls.helper._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

trait MetaRxImpl extends ReactiveLibrary with DefaultSignalObject with DefaultEventObject with DefaultReassignableVar with ReactiveLibraryImplementationHelper {
  metaRxImpl =>

  def implementationName: String = "MetaRxImpl"

  case class Cancelable(wrapped: ReadChannel[Unit]) extends ReactiveLibrary.Cancelable {
    override def kill(): Unit = wrapped.dispose()
  }

  class EventSourceImpl[+A, D <: A](private[MetaRxImpl] val wrapped: ReadChannel[D]) extends EventTrait[A] {
    type F[+X] = EventSourceImpl[X, _ <: X]

    override def observe(f: A => Unit): Cancelable = {
      Cancelable(wrapped.silentAttach(f))
    }
  }

  type Event[+A] = EventSourceImpl[A, _ <: A]

  class SignalImpl[+A, D <: A](private[MetaRxImpl] val wrapped: ReadStateChannel[D]) extends SignalTrait[A, TrackDependency] {
    type F[+B] = SignalImpl[B, _ <: B]

    def now: A = wrapped.get

    def observe(f: A => Unit): Cancelable = {
      Cancelable(wrapped.attach(f))
    }

    override def apply()(implicit trackDependency: TrackDependency): A =
      throw new UnsupportedOperationException("Signal.apply is not supported by MetaRxImpl")
  }

  type Signal[+A] = SignalImpl[A, _ <: A]

  object marmolataDiveSignalTypeclass extends SignalOperationsTrait[Signal] with Monad[Signal] with TailRecMImpl[Signal] {
    def pure[A](x: A): SignalImpl[A, _ <: A] = {
      new SignalImpl(metarx.Var(x))
    }

    override def map[A, B](fa: SignalImpl[A, _ <: A])(f: (A) => B): SignalImpl[B, _ <: B] = {
      new SignalImpl(fa.wrapped.map(f).distinct.cache(f(fa.wrapped.get)))
    }

    override def ap[A, B](ff: SignalImpl[(A) => B, _ <: (A) => B])(fa: SignalImpl[A, _ <: A]): SignalImpl[B, _ <: B] =
      new SignalImpl(ff.wrapped.zip(fa.wrapped).map { case (a, b) => a(b) }.distinct.cache(ff.wrapped.get(fa.wrapped.get)))

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

  override protected[react] def flattenEvents[A](s: SignalImpl[EventSourceImpl[A, _ <: A], _ <: EventSourceImpl[A, _ <: A]]): EventSourceImpl[A, _ <: A] = ???

  override protected[react] def triggerWhen[A, B, C](s: SignalImpl[A, _ <: A], e: EventSourceImpl[B, _ <: B], f: (A, B) => C): EventSourceImpl[C, _ <: C] = {
    new EventSourceImpl[C, C](s.wrapped.zipWith(e.wrapped)(f))
  }


  override protected[react] def fold[A, B](e: Event[A], init: B, fun: (A, B) => B): Signal[B] = {
    new SignalImpl(e.wrapped.foldLeft(init) {(acc, ev) =>
      fun(ev, acc)
    }.distinct.cache(init))
  }

  override protected[react] def signalToTry[A](from: SignalImpl[A, _ <: A]): SignalImpl[Try[A], _ <: Try[A]] = {
    marmolataDiveSignalTypeclass.map(from)(Success.apply)
  }

  def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = {
    new EventSourceImpl(FutureToReadChannel(f))
  }

  object marmolataDiveEventTypeclass extends EventOperationsTrait[Event] {
    def merge[A](x1: EventSourceImpl[A, _ <: A], x2: EventSourceImpl[A, _ <: A]): EventSourceImpl[A, _ <: A] =
      new EventSourceImpl(x1.wrapped.map(x => x: A) merge x2.wrapped.map(x => x: A))

    override def filter[A](v: EventSourceImpl[A, _ <: A], cond: (A) => Boolean): EventSourceImpl[A, _ <: A] =
      new EventSourceImpl(v.wrapped.filter(cond))

    override def map[A, B](fa: EventSourceImpl[A, _ <: A])(f: (A) => B): EventSourceImpl[B, _ <: B] = {
      new EventSourceImpl(fa.wrapped.map(f))
    }
  }

  class Var[A](private val _wrapped: metarx.Var[A]) extends SignalImpl[A, A](_wrapped.distinct.cache(_wrapped.get)) with VarTrait[A, TrackDependency] {
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

  object EventSource extends EventSourceCompanionObject[Event, EventSource] {
    def apply[A](): EventSource[A] = new EventSource(metarx.Channel[A])
  }

  object unsafeImplicits extends UnsafeImplicits {
    override implicit val marmolataDiveSignalTypeclass = metaRxImpl.marmolataDiveSignalTypeclass
  }
}
