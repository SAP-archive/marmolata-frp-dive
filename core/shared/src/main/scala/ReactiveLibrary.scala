package react

import react.cat.{Filterable, Mergeable}

import scala.concurrent.{ExecutionContext, Future}
import cats._

import scala.language.higherKinds

object ReactiveLibrary {
  trait VarCompanionObject[Var[_]] {
    /**
      * returns a new assignable variable with initial value init
      */
    def apply[A](init: A): Var[A]
  }

  trait SignalCompanionObject[Signal[_]] {
    /**
     * returns a constant signal that never changes its initial value
     * this is the same as [[cats.Applicative#pure]]
     */
    def Const[A](value: A): Signal[A]
  }

  trait EventSourceCompanionObject[Event[_], NativeEvent[_]] {
    def apply[A](): NativeEvent[A]
  }

  trait EventCompanionObject[Event[_]] {
    def Never: Event[Nothing]
  }

  trait Observable[+A] {
    /**
      * observe the associated signal or event
      * This has slightly different semantics with Signals and Events:
      *
      * == Usage as Signal ==
      * When used on a a signal, the function f is triggered whenever the signal changes its value incuding once
      * when observe is called. f is only ever called when the value changes afterwards.
      *
      * If the method shouldn't be called directly, but only when it first changes its value, use [[ReactiveLibraryUsage#SignalExtensions#]]
      *
      * == Usage as Event ==
      * When used on an event, observe is called each time the event triggers. Thus it's not called directly after observe is called.
      * Also, differently form the Signal case, it can be called with the same value twice in succession.
      *
      * == Additional information ==
      * Besides future compositon methods like [[ReactiveLibraryUsage#SignalFutureExtensions#executeFuture]] this is
      * the most important method to create side effects. It's especially not allowed to use [[Functor#map]]
      * to do side effects. Instead, use this method
      *
      * @param f the method to call
      * @return An object with which you can stop listening to the underlying Signal/event. When you call
      */
    def observe(f: A => Unit): Cancelable
  }

  trait SignalTrait[+A] extends Observable[A] {
    /**
      * returns the current value of this Signal.
      * It's generally better to use composition methods like [[Functor#map]], [[Cartesian#product]],
      * [[ReactiveLibraryUsage#SignalExtensions#triggerWhen]] and similar methods.
      * It should also be avoided to use now inside of any of these methods since the current value
      * of a signal could be in an inconsistent state
     */
    def now: A

    @deprecated("use now instead", "0.32")
    final def get: A = now
  }

  trait EventTrait[+A] extends Observable[A]

  trait EventOperationsTrait[F[+_]] extends Functor[F] with Filterable[F] with Mergeable[F]
  trait SignalOperationsTrait[F[+_]] extends Applicative[F]

  trait VarTrait[A] extends SignalTrait[A] {
    /**
      * update the value of this Var. This potentially triggers
      * value changes of dependent variables and events
      * and can also trigger side effects when any of those
      * have an attached [[Observable#observe]] method.
      *
      * Note, that this is a noop when newValue is the old value of this variable
      */
    def update(newValue: A): Unit

    /** alias for update to use in infix notation */
    @inline
    final def := (newValue: A): Unit = update(newValue)
  }

  trait EventSourceTrait[A] {
    def emit(value: A): Unit

    @inline
    final def := (value: A): Unit = emit(value)
  }

  trait Cancelable {
    /** stop calling the associated function of the observe method that returned this object */
    def kill(): Unit
  }
}

import ReactiveLibrary._


trait ReactiveLibrary {
  // deliberately make Event and Signal into volatile types
  // to be able to override them,
  // see also http://stackoverflow.com/questions/37493183/how-to-make-scala-type-volatile-on-purpose
  protected type VolatileHelper

  /**
    * one of the tow most important types of this library besides [[Signal]]
    */
  type Event[+A] <: EventTrait[A] with VolatileHelper

  /**
    * a time varying value and the other most important type of this library besides [[Event]]
    */
  type Signal[+A] <: SignalTrait[A] with VolatileHelper

  /** a time varying value that can be changed (and is generally only changed)
    * when it's [[VarTrait#update]] method is called
    */
  type Var[A] <: Signal[A] with VarTrait[A]
  type EventSource[A] <: Event[A] with EventSourceTrait[A]

  implicit val eventApplicative: EventOperationsTrait[Event]
  implicit val signalApplicative: SignalOperationsTrait[Signal]

  trait UnsafeImplicits {
    implicit val eventApplicative: FlatMap[Event] with EventOperationsTrait[Event]
    implicit val signalApplicative: FlatMap[Signal] with SignalOperationsTrait[Signal]
  }

  // use these with care as these operations are often leaking
  val unsafeImplicits: UnsafeImplicits

  val Var: VarCompanionObject[Var]
  val EventSource: EventSourceCompanionObject[Event, EventSource]

  val Signal: SignalCompanionObject[Signal]
  val Event: EventCompanionObject[Event]

  protected[react] def toSignal[A] (init: A, event: Event[A]): Signal[A]
  protected[react] def toEvent[A] (signal: Signal[A]): Event[A]
  protected[react] def futureToEvent[A] (f: Future[A])(implicit ec: ExecutionContext): Event[A]
  protected[react] def triggerWhen[A, B, C] (s: Signal[A], e: Event[B], f: (A, B) => C): Event[C]

  def implementationName: String
}
