package react.impls

import monix.execution.{Cancelable => MonixCancelable, Ack}
import monix.execution.Ack.Continue
import monix.reactive.observers.Subscriber
import react.ReactiveLibrary.Cancelable
import react.ReactiveLibrary
import react.ReactiveLibrary._

import monix.reactive.Observable

import monix.execution.Scheduler.Implicits.global
import react.impls.helper.NonCancelable

import scala.collection.mutable.MutableList
import scala.concurrent.Future


object MonixImpl extends ReactiveLibrary {
  class EventSource[+A](private[MonixImpl] val wrapped: Observable[A]) extends Monadic[A] with Observer[A] with Filterable[EventSource, A] {
    type F[+X] = EventSource[X]

    def flatMap[B](f: (A) => F[B]): F[B] = {
      new EventSource(wrapped.switchMap { x => f(x).wrapped })
    }

    def map[B](f: (A) => B): F[B] = {
      new EventSource(wrapped.map(f))
    }

    def filter(f: (A) => Boolean): EventSource[A] = {
      new EventSource(wrapped.filter(f))
    }

    def observe(f: A => Unit): Cancelable = {
      wrapped.subscribe { (x: A) => {
          f(x)
          Continue
        }
      }
      NonCancelable
    }
  }

  // TODO: get rid of asInstanceOf / check that this is safe
  class Signal[+A](private[MonixImpl] val wrapped: Observable[A], private[MonixImpl] var currentVal: Any) extends Monadic[A] with Observer[A] with SignalTrait[A] {
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

  def toSignal[A](init: A, event: EventSource[A]): Signal[A] = new Signal(event.wrapped, init)

  override def implementationName: String = "MonixImpl"

  override def toEvent[A](signal: Signal[A]): MonixImpl.EventSource[A] = new EventSource(signal.wrapped)

  class VarImpl[A](var value: A) extends Observable[A] {
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
}
