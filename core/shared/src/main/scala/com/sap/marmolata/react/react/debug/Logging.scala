package com.sap.marmolata.react.react.debug

import cats._
import com.sap.marmolata.react.api.ReactiveLibrary
import com.sap.marmolata.react.react.core.ReactiveLibrary
import com.sap.marmolata.react.react.impls.helper.ReactiveLibraryImplementationHelper

import scala.concurrent.{ExecutionContext, Future}
import language.higherKinds
import scala.util.Try

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

  def onNew(u: HasUnderlying[Annotateable]): Unit = {}
  private def withOnNew(u: HasUnderlying[Annotateable]): u.type = {
    onNew(u)
    u
  }

  override type TrackDependency = underlying.TrackDependency

  class WrappedCancelable(val u: Cancelable) extends Cancelable with HasUnderlying[Cancelable] {
    override def kill(): Unit = u.kill()

    override def under: Cancelable = u

    override def addAnnotation(annotation: Annotation): Unit = u.addAnnotation(annotation)
    override def allAnnotations: Seq[Annotation] = u.allAnnotations
  }

  class Signal[+A](private[DebugLayer] val u: underlying.Signal[A]) extends SignalTrait[A, TrackDependency] with HasUnderlying[SignalTrait[A, TrackDependency]] {
    override def now: A = u.now

    override def observe(f: (A) => Unit): Cancelable = newSignalObservable(this, f)

    override def under: SignalTrait[A, TrackDependency] = u

    override def apply()(implicit trackDependency: underlying.TrackDependency): A = u()

    override def addAnnotation(annotation: Annotation): Unit = u.addAnnotation(annotation)

    override def allAnnotations: Seq[Annotation] = u.allAnnotations
  }

  class Var[A](u: underlying.Var[A]) extends Signal[A](u) with VarTrait[A, TrackDependency] {
    override def update(newValue: A): Unit = u.update(newValue)
  }

  class Event[+A](private[DebugLayer] val u: underlying.Event[A]) extends EventTrait[A] with HasUnderlying[EventTrait[A]] {
    override def observe(f: (A) => Unit): Cancelable = newEventObservable(this, f)

    override def under: EventTrait[A] = u

    override def addAnnotation(annotation: Annotation): Unit = u.addAnnotation(annotation)
    override def allAnnotations: Seq[Annotation] = u.allAnnotations
  }

  class EventSource[A](u: underlying.EventSource[A]) extends Event[A](u) with EventSourceTrait[A] {
    override def emit(value: A): Unit = u.emit(value)
  }

  class ReassignableVar[A](private[DebugLayer] val u: underlying.ReassignableVar[A])
    extends ReassignableVarTrait[A, Signal, TrackDependency] with HasUnderlying[VarTrait[A, TrackDependency]] {
    override def update(newValue: A): Unit = u.update(newValue)

    override def now: A = u.now

    override def observe(f: (A) => Unit): Cancelable = newObservable(this, f)

    override def subscribe(s: Signal[A]): Unit = u.subscribe(s.u)

    override lazy val toSignal: Signal[A] = new Signal(u.toSignal)

    override def under: VarTrait[A, TrackDependency] = u

    override def apply()(implicit trackDependency: underlying.TrackDependency): A = u()

    override def addAnnotation(annotation: Annotation): Unit = u.addAnnotation(annotation)
    override def allAnnotations: Seq[Annotation] = u.allAnnotations
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

  override object Signal extends SignalCompanionObject[Signal, TrackDependency] {
    override def Const[A](value: A): Signal[A] =
      newSignal(underlying.Signal.Const(value))

    override def apply[A](fun: (TrackDependency) => A): Signal[A] = {
      newSignal(underlying.Signal(fun))
    }

    override def breakPotentiallyLongComputation()(implicit td: underlying.TrackDependency): Unit = {
      underlying.Signal.breakPotentiallyLongComputation()(td)
    }
  }

  override object Event extends EventCompanionObject[Event] {
    override lazy val Never: Event[Nothing] =
      newEvent(underlying.Event.Never)
  }


  override protected[react] def triggerWhen[A, B, C](s: Signal[A], e: Event[B], f: (A, B) => C): Event[C] =
    newEvent(underlying.triggerWhen(s.u, e.u, f))


  override protected[react] def fold[A, B](e: Event[A], init: B, fun: (A, B) => B): Signal[B] = {
    newSignal(underlying.fold(e.u, init, fun))
  }

  override protected[react] def flattenEvents[A](s: Signal[Event[A]]): Event[A] = {
    newEvent(underlying.flattenEvents(underlying.marmolataDiveSignalTypeclass.map(s.u)(_.u)))
  }

  override protected[react] def signalToTry[A](from: Signal[A]): Signal[Try[A]] = {
    newSignal(underlying.signalToTry(from.u))
  }

  trait _eventApplicative extends EventOperationsTrait[Event] {
    override def map[A, B](fa: Event[A])(f: (A) => B): Event[B] =
      newEvent(underlying.marmolataDiveEventTypeclass.map(fa.u)(f))

    override def merge[A](x1: Event[A], x2: Event[A]): Event[A] =
      newEvent(underlying.marmolataDiveEventTypeclass.merge(x1.u, x2.u))

    override def filter[A](v: Event[A], cond: (A) => Boolean): Event[A] =
      newEvent(underlying.marmolataDiveEventTypeclass.filter(v.u, cond))

    override def lift[A, B](f: (A) => B): (Event[A]) => Event[B] =
      ev => newEvent(underlying.marmolataDiveEventTypeclass.lift(f)(ev.u))

    override def void[A](fa: Event[A]): Event[Unit] =
      newEvent(underlying.marmolataDiveEventTypeclass.void(fa.u))

    override def fproduct[A, B](fa: Event[A])(f: (A) => B): Event[(A, B)] =
      newEvent(underlying.marmolataDiveEventTypeclass.fproduct(fa.u)(f))

    override def as[A, B](fa: Event[A], b: B): Event[B] =
      newEvent(underlying.marmolataDiveEventTypeclass.as(fa.u, b))
  }

  implicit override object marmolataDiveEventTypeclass extends _eventApplicative

  override implicit object marmolataDiveSignalTypeclass extends Monad[Signal] with SignalOperationsTrait[Signal] {
    override def pure[A](x: A): Signal[A] =
      newSignal(underlying.marmolataDiveSignalTypeclass.pure(x))

    override def ap[A, B](ff: Signal[(A) => B])(fa: Signal[A]): Signal[B] =
      newSignal(underlying.marmolataDiveSignalTypeclass.ap(ff.u)(fa.u))

    override def tailRecM[A, B](a: A)(f: A => Signal[Either[A, B]]): Signal[B] =
      newSignal(underlying.unsafeImplicits.marmolataDiveSignalTypeclass.tailRecM(a)(f.andThen(x => x.u)))

    override def map[A, B](fa: Signal[A])(f: (A) => B): Signal[B] =
      newSignal(underlying.marmolataDiveSignalTypeclass.map(fa.u)(f))

    override def replicateA[A](n: Int, fa: Signal[A]): Signal[List[A]] =
      newSignal(underlying.marmolataDiveSignalTypeclass.replicateA(n, fa.u))

    override def traverse[A, G[_], B](value: G[A])(f: (A) => Signal[B])(implicit G: Traverse[G]): Signal[G[B]] =
      newSignal(underlying.marmolataDiveSignalTypeclass.traverse(value)(f.andThen(_.u)))

    override def sequence[G[_], A](as: G[Signal[A]])(implicit G: Traverse[G]): Signal[G[A]] =
      newSignal(underlying.marmolataDiveSignalTypeclass.sequence(G.map(as)(_.u)))

    override def product[A, B](fa: Signal[A], fb: Signal[B]): Signal[(A, B)] =
      newSignal(underlying.marmolataDiveSignalTypeclass.product(fa.u, fb.u))

    override def ap2[A, B, Z](ff: Signal[(A, B) => Z])(fa: Signal[A], fb: Signal[B]): Signal[Z] =
      newSignal(underlying.marmolataDiveSignalTypeclass.ap2(ff.u)(fa.u, fb.u))

    override def map2[A, B, Z](fa: Signal[A], fb: Signal[B])(f: (A, B) => Z): Signal[Z] =
      newSignal(underlying.marmolataDiveSignalTypeclass.map2(fa.u, fb.u)(f))

    override def lift[A, B](f: (A) => B): (Signal[A]) => Signal[B] =
      s => newSignal(underlying.marmolataDiveSignalTypeclass.lift(f)(s.u))

    override def void[A](fa: Signal[A]): Signal[Unit] =
      newSignal(underlying.marmolataDiveSignalTypeclass.void(fa.u))

    override def fproduct[A, B](fa: Signal[A])(f: (A) => B): Signal[(A, B)] =
      newSignal(underlying.marmolataDiveSignalTypeclass.fproduct(fa.u)(f))

    override def as[A, B](fa: Signal[A], b: B): Signal[B] =
      newSignal(underlying.marmolataDiveSignalTypeclass.as(fa.u, b))

    override def tuple2[A, B](f1: Signal[A], f2: Signal[B]): Signal[(A, B)] =
      newSignal(underlying.marmolataDiveSignalTypeclass.tuple2(f1.u, f2.u))

    override def ap3[A0, A1, A2, Z](f: Signal[(A0, A1, A2) => Z])(f0: Signal[A0], f1: Signal[A1], f2: Signal[A2]): Signal[Z] =
      newSignal(underlying.marmolataDiveSignalTypeclass.ap3(f.u)(f0.u, f1.u, f2.u))

    override def map3[A0, A1, A2, Z](f0: Signal[A0], f1: Signal[A1], f2: Signal[A2])(f: (A0, A1, A2) => Z): Signal[Z] =
      newSignal(underlying.marmolataDiveSignalTypeclass.map3(f0.u, f1.u, f2.u)(f))

    override def tuple3[A0, A1, A2, Z](f0: Signal[A0], f1: Signal[A1], f2: Signal[A2]): Signal[(A0, A1, A2)] =
      newSignal(underlying.marmolataDiveSignalTypeclass.tuple3(f0.u, f1.u, f2.u))

    //TODO: implement apXX, mapXX and tupleXX (for XX = 4 - 22)
    // (this has to be done because the underlying library might do something more efficient than the default implementation)

    override def flatMap[A, B](fa: Signal[A])(f: (A) => Signal[B]): Signal[B] =
      newSignal(underlying.unsafeImplicits.marmolataDiveSignalTypeclass.flatMap(fa.u)(f.andThen(_.u)))

    override def flatten[A](ffa: Signal[Signal[A]]): Signal[A] =
      newSignal(underlying.unsafeImplicits.marmolataDiveSignalTypeclass.flatten(underlying.marmolataDiveSignalTypeclass.map(ffa.u)(_.u)))

    override def mproduct[A, B](fa: Signal[A])(f: (A) => Signal[B]): Signal[(A, B)] =
      newSignal(underlying.unsafeImplicits.marmolataDiveSignalTypeclass.mproduct(fa.u)(f.andThen(_.u)))

    override def ifM[B](fa: Signal[Boolean])(ifTrue: => Signal[B], ifFalse: => Signal[B]): Signal[B] =
      newSignal(underlying.unsafeImplicits.marmolataDiveSignalTypeclass.ifM(fa.u)(ifTrue.u, ifFalse.u))
  }


  override object unsafeImplicits extends UnsafeImplicits {
    override implicit val marmolataDiveSignalTypeclass: Monad[Signal] with SignalOperationsTrait[Signal] = self.marmolataDiveSignalTypeclass
  }
}
