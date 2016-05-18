package react

import scala.concurrent.{ExecutionContext, Future}


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
  trait Monadic[+A] {
    self =>
    type F[+B] <: Monadic[B] { type F[C] = self.F[C] }

    def map[B](f: A => B): F[B]

    def flatMap[B](f: A => F[B]): F[B]

    def zip[B] (other: F[B]): F[(A, B)] = {
      for {
        p1 <- this
        p2 <- other
      } yield ((p1, p2))
    }

    def zipWith[B, C](other: F[B])(fun: (A, B) => C): F[C] = {
      for {
        p1 <- this
        p2 <- other
      } yield (fun(p1, p2))
    }
  }

  trait Filterable[+A] {
    self =>
    type F[+B] <: Filterable[B] with Monadic[B] { type F[C] = self.F[C] }

    def filter(f: A => Boolean): F[A]

    def mapPartial[B](f: PartialFunction[A, B]): F[B] = {
      filter(f.isDefinedAt(_)).map(f.apply(_))
    }
  }

  trait VarCompanionObject[Var[_]] {
    def apply[A](init: A): Var[A]
  }

  trait EventCompanionObject[NativeEvent[_]] {
    def apply[A](): NativeEvent[A]
  }

  trait SignalTrait[+A] {
    def now: A
  }

  trait VarTrait[A] extends SignalTrait[A] {
    def update(newValue: A): Unit

    @inline
    final def := (newValue: A): Unit = update(newValue)
  }

  trait NativeEventTrait[A] {
    def emit(value: A): Unit
  }

  trait Observable[+A] {
    def observe(f: A => Unit): Cancelable
    //def killAll(): Unit
  }

  trait Cancelable {
    def kill(): Unit
  }

  trait ConstCompanionObject[Signal[_]] {
    def apply[A](value: A): Signal[A]
  }
}

trait ReactiveLibrary {
  import ReactiveLibrary._
  type Event[+A] <: (Monadic[A] { type F[B] = Event[B] }) with Observable[A] with Filterable[A]
  type Signal[+A] <: (Monadic[A] { type F[B] = Signal[B] }) with SignalTrait[A] with Observable[A]

  type Var[A] <: VarTrait[A] with Signal[A]
  type EventSource[A] <: NativeEventTrait[A] with Event[A]

  val Var: VarCompanionObject[Var]
  val Event: EventCompanionObject[EventSource]
  val Const: ConstCompanionObject[Var]

  def toSignal[A] (init: A, event: Event[A]): Signal[A]
  def toEvent[A] (signal: Signal[A]): Event[A]
  def futureToEvent[A] (f: Future[A])(implicit ec: ExecutionContext): Event[A]

  def implementationName: String
}