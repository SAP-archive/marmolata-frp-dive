package react.impls

import monix.execution.{Cancelable => MonixCancelable, Ack}
import monix.execution.Ack.Continue
import monix.reactive.observers.Subscriber
import react.ReactiveLibrary.Cancelable
import react.ReactiveLibrary
import react.ReactiveLibrary._

import monix.reactive.{Observable => MonixObservable}

import monix.execution.Scheduler.Implicits.global
import react.impls.helper.{DefaultConstObject, NonCancelable}

import scala.collection.mutable.MutableList
import scala.concurrent.{ExecutionContext, Future}


trait MonixImpl extends ReactiveLibrary with DefaultConstObject {
  class Event[+A](private[MonixImpl] val wrapped: MonixObservable[A]) extends EventTrait[A] {
    type F[+X] = Event[X]

    def flatMap[B](f: (A) => F[B]): F[B] = {
      new Event(wrapped.switchMap { x => f(x).wrapped })
    }

    def map[B](f: (A) => B): F[B] = {
      new Event(wrapped.map(f))
    }

    def filter(f: (A) => Boolean): Event[A] = {
      new Event(wrapped.filter(f))
    }

    def observe(f: A => Unit): Cancelable = {
      wrapped.subscribe { (x: A) => {
          f(x)
          Continue
        }
      }
      NonCancelable
    }

    override def merge[B >: A](other: Event[B]): Event[B] = ???
  }

  // TODO: get rid of asInstanceOf / check that this is safe
  class Signal[+A](private[MonixImpl] val wrapped: MonixObservable[A], private[MonixImpl] var currentVal: Any) extends Monadic[A] with Observable[A] with SignalTrait[A] {
    type F[+X] = Signal[X]

    wrapped.subscribe { x => currentVal = x; Continue }

    def flatMap[B](f: A => F[B]): F[B] = {
      new Signal(wrapped.switchMap { (x: A) => f(x).wrapped }, f(currentVal.asInstanceOf[A]).currentVal)
    }

    def now: A = currentVal.asInstanceOf[A]

    def map[B](f: (A) => B): F[B] =
      new Signal(wrapped.map(f), f(currentVal.asInstanceOf[A]))

    override def observe(f: (A) => Unit): Cancelable = {
      f(currentVal.asInstanceOf[A])
      wrapped.subscribe { (x: A) => {
        f(x)
        Continue
      }}
      NonCancelable
    }
  }

  def toSignal[A](init: A, event: Event[A]): Signal[A] = new Signal(event.wrapped, init)

  override def implementationName: String = "MonixImpl"

  override def toEvent[A](signal: Signal[A]): Event[A] = new Event(signal.wrapped)

  override def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = ???

  class VarImpl[A](var value: A) extends MonixObservable[A] {
    //TODO: handle continuations etc.

    val subscriptions: MutableList[Option[Subscriber[A]]] = MutableList.empty

    def unsafeSubscribeFn(subscriber: Subscriber[A]): MonixCancelable = {
      subscriptions += Some(subscriber)
      val index = subscriptions.length - 1
      MonixCancelable { () =>
        subscriptions.update(index, None)
      }
    }

    def update(newVal: A) = {
      value = newVal
      subscriptions foreach { _.foreach { _.onNext(newVal) } }
    }
  }

  class Var[A](private val _wrapped: VarImpl[A]) extends Signal[A](_wrapped, _wrapped.value) with VarTrait[A] {
    override def update(newValue: A): Unit = _wrapped.update(newValue)
  }

  object Var extends VarCompanionObject[Var] {
    override def apply[A](init: A): Var[A] = new Var(new VarImpl(init))
  }

  class EventSource[A](_wrapped: monix.reactive.Observable[A]) extends Event[A](_wrapped) with NativeEventTrait[A] {
    override def emit(value: A): Unit = ???
  }

  object Event extends EventCompanionObject[EventSource] {
    override def apply[A](): EventSource[A] = ???
  }
}
