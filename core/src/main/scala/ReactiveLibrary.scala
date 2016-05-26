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

  trait EventCompanionObject[NativeEvent[_]] {
    def apply[A](): NativeEvent[A]
  }

  trait Observable[+A] {
    def observe(f: A => Unit): Cancelable
  }

  trait SignalTrait[+A] extends Observable[A] {
    def now: A
  }

  trait EventTrait[+A] extends Observable[A]

  trait EventOperationsTrait[F[+_]] extends Apply[F] with Filterable[F] with Mergeable[F]
  trait SignalOperationsTrait[F[+_]] extends Applicative[F]

  trait VarTrait[A] extends SignalTrait[A] {
    def update(newValue: A): Unit

    @inline
    final def := (newValue: A): Unit = update(newValue)
  }

  trait EventSourceTrait[A] {
    def emit(value: A): Unit
  }

  trait Cancelable {
    def kill(): Unit
  }

  trait ConstCompanionObject[Signal[_]] {
    def apply[A](value: A): Signal[A]
  }

  trait SubscriptionSignal {

  }
}

trait ReactiveLibrary {
  import ReactiveLibrary._
  type Event[+A] <: EventTrait[A] { type F[B] = Event[B] }
  type Signal[+A] <: SignalTrait[A] { type F[B] = Signal[B] }

  type Var[A] <: VarTrait[A] with Signal[A]
  type EventSource[A] <: EventSourceTrait[A] with Event[A]

  implicit val eventApplicative: EventOperationsTrait[Event]
  implicit val signalApplicative: SignalOperationsTrait[Signal]

  trait UnsafeImplicits {
    implicit val eventApplicative: FlatMap[Event] with EventOperationsTrait[Event]
    implicit val signalApplicative: FlatMap[Signal] with SignalOperationsTrait[Signal]
  }

  // use these with care as these operations are often leaking
  val unsafeImplicits: UnsafeImplicits

  val Var: VarCompanionObject[Var]
  val Event: EventCompanionObject[EventSource]
  val Const: ConstCompanionObject[Var]

  protected [react] def toSignal[A] (init: A, event: Event[A]): Signal[A]
  protected [react] def toEvent[A] (signal: Signal[A]): Event[A]
  protected [react] def futureToEvent[A] (f: Future[A])(implicit ec: ExecutionContext): Event[A]
  protected [react] def triggerWhen[A, B, C] (s: Signal[A], e: Event[B], f: (A, B) => C): Event[C]

  def implementationName: String
}