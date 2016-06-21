package react.debug

import cats._
import react.ReactiveLibrary
import react.ReactiveLibrary._
import react.impls.helper.ReactiveLibraryImplementationHelper

import scala.concurrent.{ExecutionContext, Future}

import language.higherKinds

trait HasUnderlying[+A] {
  def under: A
}

class DebugLayer(protected val underlying: ReactiveLibrary)
  extends ReactiveLibrary with ReactiveLibraryImplementationHelper {
  self =>

  def newReassignableVar[A](u: underlying.ReassignableVar[A]): self.ReassignableVar[A] =
    withOnNew(new ReassignableVar(u))

  def newVar[A](u: underlying.Var[A]): self.Var[A] =
    withOnNew(new Var(u))

  def newEvent[A](u: underlying.Event[A]): self.Event[A] =
    withOnNew(new Event(u))

  def newEventSource[A](u: underlying.EventSource[A]): self.EventSource[A] =
    withOnNew(new EventSource(u))

  def newSignal[A](u: underlying.Signal[A]): self.Signal[A] =
    withOnNew(new Signal(u))

  def newSignalObservable[A](s: Signal[A], f: A => Unit): Cancelable =
    newObservable(s, f)

  def newEventObservable[A](s: Event[A], f: A => Unit): Cancelable =
    newObservable(s, f)

  def newObservable[A](s: HasUnderlying[Observable[A]], f: A => Unit): Cancelable =
    newCancelable(s.under.observe(f))

  def newCancelable(s: Cancelable): Cancelable =
    withOnNew(new WrappedCancelable(s))

  def onNew(u: HasUnderlying[Nameable]): Unit = {}
  private def withOnNew(u: HasUnderlying[Nameable]): u.type = {
    onNew(u)
    u
  }

  class WrappedCancelable(val u: Cancelable) extends Cancelable with HasUnderlying[Cancelable] {
    override def kill(): Unit = u.kill

    override def under: Cancelable = u

    override def name: String = u.name

    override def name_=(s: String): Unit = u.name = s
  }

  class Signal[+A](private[DebugLayer] val u: underlying.Signal[A]) extends SignalTrait[A] with HasUnderlying[SignalTrait[A]] {
    override def now: A = u.now

    override def observe(f: (A) => Unit): Cancelable = newSignalObservable(this, f)

    override def under: SignalTrait[A] = u
  }

  class Var[A](u: underlying.Var[A]) extends Signal[A](u) with VarTrait[A] {
    override def update(newValue: A): Unit = u.update(newValue)
  }

  class Event[+A](private[DebugLayer] val u: underlying.Event[A]) extends EventTrait[A] with HasUnderlying[EventTrait[A]] {
    override def observe(f: (A) => Unit): Cancelable = newEventObservable(this, f)

    override def under: EventTrait[A] = u
  }

  class EventSource[A](u: underlying.EventSource[A]) extends Event[A](u) with EventSourceTrait[A] {
    override def emit(value: A): Unit = u.emit(value)
  }

  class ReassignableVar[A](private[DebugLayer] val u: underlying.ReassignableVar[A])
    extends ReassignableVarTrait[A, Signal] with HasUnderlying[VarTrait[A]] {
    override def update(newValue: A): Unit = u.update(newValue)

    override def now: A = u.now

    override def observe(f: (A) => Unit): Cancelable = newObservable(this, f)

    override def subscribe(s: Signal[A]): Unit = u.subscribe(s.u)

    override lazy val toSignal: Signal[A] = new Signal(u.toSignal)

    override def under: VarTrait[A] = u
  }

  override protected[react] def toSignal[A](init: A, event: Event[A]): Signal[A] = {
    new Signal(underlying.toSignal(init, event.u))
  }

  override protected[react] def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): Event[A] = {
    newEvent(underlying.futureToEvent(f))
  }

  override def implementationName: String = s"DebugLayer-of-${underlying.implementationName}"

  override protected[react] def toEvent[A](signal: Signal[A]): Event[A] = {
    newEvent(underlying.toEvent(signal.u))
  }

  override object Var extends VarCompanionObject[Var] {
    override def apply[A](init: A): Var[A] =
      newVar(underlying.Var(init))
  }

  override object EventSource extends EventSourceCompanionObject[Event, EventSource] {
    override def apply[A](): EventSource[A] =
      newEventSource(underlying.EventSource())
  }

  override object ReassignableVar extends ReassignableVarCompanionObject[ReassignableVar, Signal] {
    override def apply[A](init: A): ReassignableVar[A] =
      newReassignableVar(underlying.ReassignableVar(init))

    override def apply[A](init: Signal[A]): ReassignableVar[A] =
      newReassignableVar(underlying.ReassignableVar(init.u))
  }

  override object Signal extends SignalCompanionObject[Signal] {
    override def Const[A](value: A): Signal[A] =
      newSignal(underlying.Signal.Const(value))
  }

  override object Event extends EventCompanionObject[Event] {
    override lazy val Never: Event[Nothing] =
      newEvent(underlying.Event.Never)
  }


  override protected[react] def triggerWhen[A, B, C](s: Signal[A], e: Event[B], f: (A, B) => C): Event[C] =
    newEvent(underlying.triggerWhen(s.u, e.u, f))

  override implicit object eventApplicative extends FlatMap[Event] with EventOperationsTrait[Event] {
    override def map[A, B](fa: Event[A])(f: (A) => B): Event[B] =
      newEvent(underlying.eventApplicative.map(fa.u)(f))

    override def merge[A](x1: Event[A], x2: Event[A]): Event[A] =
      newEvent(underlying.eventApplicative.merge(x1.u, x2.u))

    override def filter[A](v: Event[A], cond: (A) => Boolean): Event[A] =
      newEvent(underlying.eventApplicative.filter(v.u, cond))

    override def lift[A, B](f: (A) => B): (Event[A]) => Event[B] =
      ev => newEvent(underlying.eventApplicative.lift(f)(ev.u))

    override def void[A](fa: Event[A]): Event[Unit] =
      newEvent(underlying.eventApplicative.void(fa.u))

    override def fproduct[A, B](fa: Event[A])(f: (A) => B): Event[(A, B)] =
      newEvent(underlying.eventApplicative.fproduct(fa.u)(f))

    override def as[A, B](fa: Event[A], b: B): Event[B] =
      newEvent(underlying.eventApplicative.as(fa.u, b))

    override def flatMap[A, B](fa: Event[A])(f: (A) => Event[B]): Event[B] =
      newEvent(underlying.unsafeImplicits.eventApplicative.flatMap(fa.u)(f.andThen(_.u)))
  }

  override implicit object signalApplicative extends Monad[Signal] with SignalOperationsTrait[Signal] {
    override def pure[A](x: A): Signal[A] =
      newSignal(underlying.signalApplicative.pure(x))

    override def ap[A, B](ff: Signal[(A) => B])(fa: Signal[A]): Signal[B] =
      newSignal(underlying.signalApplicative.ap(ff.u)(fa.u))

    override def pureEval[A](x: Eval[A]): Signal[A] =
      newSignal(underlying.signalApplicative.pureEval(x))

    override def map[A, B](fa: Signal[A])(f: (A) => B): Signal[B] =
      newSignal(underlying.signalApplicative.map(fa.u)(f))

    override def replicateA[A](n: Int, fa: Signal[A]): Signal[List[A]] =
      newSignal(underlying.signalApplicative.replicateA(n, fa.u))

    override def traverse[A, G[_], B](value: G[A])(f: (A) => Signal[B])(implicit G: Traverse[G]): Signal[G[B]] =
      newSignal(underlying.signalApplicative.traverse(value)(f.andThen(_.u)))

    override def sequence[G[_], A](as: G[Signal[A]])(implicit G: Traverse[G]): Signal[G[A]] =
      newSignal(underlying.signalApplicative.sequence(G.map(as)(_.u)))

    override def product[A, B](fa: Signal[A], fb: Signal[B]): Signal[(A, B)] =
      newSignal(underlying.signalApplicative.product(fa.u, fb.u))

    override def ap2[A, B, Z](ff: Signal[(A, B) => Z])(fa: Signal[A], fb: Signal[B]): Signal[Z] =
      newSignal(underlying.signalApplicative.ap2(ff.u)(fa.u, fb.u))

    override def map2[A, B, Z](fa: Signal[A], fb: Signal[B])(f: (A, B) => Z): Signal[Z] =
      newSignal(underlying.signalApplicative.map2(fa.u, fb.u)(f))

    override def lift[A, B](f: (A) => B): (Signal[A]) => Signal[B] =
      s => newSignal(underlying.signalApplicative.lift(f)(s.u))

    override def void[A](fa: Signal[A]): Signal[Unit] =
      newSignal(underlying.signalApplicative.void(fa.u))

    override def fproduct[A, B](fa: Signal[A])(f: (A) => B): Signal[(A, B)] =
      newSignal(underlying.signalApplicative.fproduct(fa.u)(f))

    override def as[A, B](fa: Signal[A], b: B): Signal[B] =
      newSignal(underlying.signalApplicative.as(fa.u, b))

    override def tuple2[A, B](f1: Signal[A], f2: Signal[B]): Signal[(A, B)] =
      newSignal(underlying.signalApplicative.tuple2(f1.u, f2.u))

    override def ap3[A0, A1, A2, Z](f: Signal[(A0, A1, A2) => Z])(f0: Signal[A0], f1: Signal[A1], f2: Signal[A2]): Signal[Z] =
      newSignal(underlying.signalApplicative.ap3(f.u)(f0.u, f1.u, f2.u))

    override def map3[A0, A1, A2, Z](f0: Signal[A0], f1: Signal[A1], f2: Signal[A2])(f: (A0, A1, A2) => Z): Signal[Z] =
      newSignal(underlying.signalApplicative.map3(f0.u, f1.u, f2.u)(f))

    override def tuple3[A0, A1, A2, Z](f0: Signal[A0], f1: Signal[A1], f2: Signal[A2]): Signal[(A0, A1, A2)] =
      newSignal(underlying.signalApplicative.tuple3(f0.u, f1.u, f2.u))

    //TODO: implement apXX, mapXX and tupleXX (for XX = 4 - 22)
    // (this has to be done because the underlying library might do something more efficient than the default implementation)

    override def flatMap[A, B](fa: Signal[A])(f: (A) => Signal[B]): Signal[B] =
      newSignal(underlying.unsafeImplicits.signalApplicative.flatMap(fa.u)(f.andThen(_.u)))

    override def flatten[A](ffa: Signal[Signal[A]]): Signal[A] =
      newSignal(underlying.unsafeImplicits.signalApplicative.flatten(underlying.signalApplicative.map(ffa.u)(_.u)))

    override def mproduct[A, B](fa: Signal[A])(f: (A) => Signal[B]): Signal[(A, B)] =
      newSignal(underlying.unsafeImplicits.signalApplicative.mproduct(fa.u)(f.andThen(_.u)))

    override def ifM[B](fa: Signal[Boolean])(ifTrue: => Signal[B], ifFalse: => Signal[B]): Signal[B] =
      newSignal(underlying.unsafeImplicits.signalApplicative.ifM(fa.u)(ifTrue.u, ifFalse.u))
  }


  override object unsafeImplicits extends UnsafeImplicits {
    override implicit val eventApplicative: FlatMap[Event] with EventOperationsTrait[Event] = self.eventApplicative
    override implicit val signalApplicative: Monad[Signal] with SignalOperationsTrait[Signal] = self.signalApplicative
  }
}