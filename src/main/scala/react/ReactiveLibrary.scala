package react

import scala.concurrent.{ExecutionContext, Future}


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
  }

  trait Filterable[F[+_], +A] {
    def filter(f: A => Boolean): F[A]
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
}

trait ReactiveLibrary {
  import ReactiveLibrary._
  type Event[+A] <: (Monadic[A] { type F[B] = Event[B] }) with Observable[A] with Filterable[Event, A]
  type Signal[+A] <: (Monadic[A] { type F[B] = Signal[B] }) with SignalTrait[A] with Observable[A]

  type Var[A] <: VarTrait[A] with Signal[A]
  type NativeEvent[A] <: NativeEventTrait[A] with Event[A]


  val Var: VarCompanionObject[Var]
  val Event: EventCompanionObject[NativeEvent]

  def toSignal[A] (init: A, event: Event[A]): Signal[A]
  def toEvent[A] (signal: Signal[A]): Event[A]
  def futureToEvent[A] (f: Future[A])(implicit ec: ExecutionContext): Event[A]

  def implementationName: String
}