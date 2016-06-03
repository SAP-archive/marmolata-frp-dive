package react

import react.cat.{Filterable, Mergeable}

import scala.concurrent.{ExecutionContext, Future}
import cats._

import scala.language.higherKinds



// TODO: think about if we want to introduce implicits for ownership
// to get rid of events/signals when they aren't used anymore
// this may be particularly important in flatMap where a new event/signal
// is created whenever a value changes and thus we may want to clean up
// earlier signals/events soonish
// if we still want to support for-notation, we probably need to do some macro-voodoo to
// introduce implicit parameters in the right places, because Scala doesn't support automatically inserted
// implicits in functions depending on their type signature
// curiously, functions don't seem to support implicits in any way, only methods do and you can't even write a type signature for them

object ReactiveLibrary {
  trait VarCompanionObject[Var[_]] {
    def apply[A](init: A): Var[A]
  }

  trait SignalCompanionObject[Signal[_]] {
    def Const[A](value: A): Signal[A]
  }

  trait EventSourceCompanionObject[Event[_], NativeEvent[_]] {
    def apply[A](): NativeEvent[A]
  }

  trait EventCompanionObject[Event[_]] {
    def Never: Event[Nothing]
  }

  trait Observable[+A] {
    def observe(f: A => Unit): Cancelable
  }

  trait SignalTrait[+A] extends Observable[A] {
    def now: A
  }

  trait EventTrait[+A] extends Observable[A]

  trait EventOperationsTrait[F[+_]] extends Functor[F] with Filterable[F] with Mergeable[F]
  trait SignalOperationsTrait[F[+_]] extends Applicative[F]

  trait VarTrait[A] extends SignalTrait[A] {
    def update(newValue: A): Unit

    @inline
    final def := (newValue: A): Unit = update(newValue)
  }

  trait EventSourceTrait[A] {
    def emit(value: A): Unit

    @inline
    final def :=(value: A) = emit(value)
  }

  trait Cancelable {
    def kill(): Unit
  }
}

import ReactiveLibrary._


trait ReactiveLibrary {
  // deliberately make Event and Signal into volatile types
  // to be able to override them,
  // see also http://stackoverflow.com/questions/37493183/how-to-make-scala-type-volatile-on-purpose
  protected type VolatileHelper

  type Event[+A] <: EventTrait[A] with VolatileHelper
  type Signal[+A] <: SignalTrait[A] with VolatileHelper

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

  protected [react] def toSignal[A] (init: A, event: Event[A]): Signal[A]
  protected [react] def toEvent[A] (signal: Signal[A]): Event[A]
  protected [react] def futureToEvent[A] (f: Future[A])(implicit ec: ExecutionContext): Event[A]
  protected [react] def triggerWhen[A, B, C] (s: Signal[A], e: Event[B], f: (A, B) => C): Event[C]

  def implementationName: String
}