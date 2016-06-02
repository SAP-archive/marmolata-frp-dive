package react

import cats._
import cats.Functor.ToFunctorOps
import cats.syntax.{AllSyntax, FlatMapOps, FunctorSyntax}
import cats.syntax.all._
import react.cat.{Mergeable, FilterableSyntax}

import scala.concurrent.{ExecutionContext, Future}

trait ReactiveLibraryUsage {
  self: ReactiveLibrary =>

  implicit final class FutureExtensions[A](f: Future[A]) {
    def toEvent(implicit ec: ExecutionContext): Event[A] = futureToEvent(f)
  }
  
  implicit final class SignalExtensions[A](s: Signal[A]) {
    def toEvent: Event[A] = self.toEvent(s)
    def triggerWhen[B, C](e: Event[B], f: (A, B) => C): Event[C] = self.triggerWhen(s, e, f)
    def triggerWhen[B](e: Event[B]): Event[A] = triggerWhen[B, A](e, (x, _) => x)
  }

  implicit final class EventExtensions[A](event: Event[A]) {
    def toSignal(init: A) = self.toSignal(init, event)
    def toSignal: Signal[Option[A]] = event.map(Some(_): Option[A]).toSignal(None)
    def triggerWhen[B, C](s: Signal[B], f: (A, B) => C): Event[C] = self.triggerWhen(s, event, (a: B, b: A) => f(b, a))
    def or(e: Event[Any]): Event[Unit] = {
      implicitly[Mergeable[Event]].merge(event.map(Function.const(Unit)), e.map(Function.const(Unit)))
    }
  }

  class ReassignableSignal[A](private[ReactiveLibraryUsage] val constr: Var[Signal[A]]) {
    def subscribe(p: Signal[A]) = constr := p
    def := (p: A) = constr := Const(p)
  }


  object ReassignableSignal {
    def apply[A](init: Signal[A]) = new ReassignableSignal(Var(init))
    def apply[A](init: A) = new ReassignableSignal[A](Var(Const(init)))
  }

  class ReassignableEvent[A](private[ReactiveLibraryUsage] val constr: Var[Event[A]]) {
    def subscribe(p: Event[A]) = constr := p
  }

  object ReassignableEvent {
    def apply[A](): ReassignableEvent[A] = new ReassignableEvent(Var(Event()))
  }

  implicit class ListCombinators[T](seq: List[Signal[T]]) {
    def sequence: Signal[List[T]] = {
      val zeroChannel: Signal[List[T]] = Var[List[T]](List.empty[T])
      seq.foldLeft(zeroChannel) {
        case (acc, readChannel) => (acc |@| readChannel) map {
          case (list, value) => list :+ value
        }
      }
    }
  }

  import scala.language.implicitConversions


  implicit def reassignableSignalToSignal[A](p: ReassignableSignal[A]): Signal[A] = {
    import self.unsafeImplicits.{signalApplicative => s}
    (p.constr: Signal[Signal[A]]).flatMap(identity)
  }

  implicit def reassignableEventToEvent[A](p: ReassignableEvent[A]): Event[A] = {
    import self.unsafeImplicits.{signalApplicative => s}
    import react.cat.filterSyntax._
    (p.constr: Signal[Event[A]]).flatMap(_.toSignal).toEvent.mapPartial { case Some(x) => x }
  }

  trait VarSyntax {
    implicit def varIsFunctor[A](v: Var[A]): Functor.Ops[Signal, A] = v: Signal[A]
    implicit def varIsApply[A](v: Var[A]): Apply.Ops[Signal, A] = v: Signal[A]
    implicit def varIsFlatMap[A](v: Var[A])(implicit signalIsMonad: FlatMap[Signal]): FlatMapOps[Signal, A] = v: Signal[A]
    implicit def varIsCartesian[A](v: Var[A]): Cartesian.Ops[Signal, A] = v: Signal[A]
  }

  trait ReassignableSignalSyntax {
    implicit def reassignableVarIsFunctor[A](v: ReassignableSignal[A]): Functor.Ops[Signal, A] = v: Signal[A]
    implicit def reassignableVarIsApply[A](v: ReassignableSignal[A]): Apply.Ops[Signal, A] = v: Signal[A]
    implicit def reassignableVarIsFlatMap[A](v: ReassignableSignal[A])(implicit signalIsMonad: FlatMap[Signal]): FlatMapOps[Signal, A] = v: Signal[A]
    implicit def reassignableVarIsCartesian[A](v: ReassignableSignal[A]): Cartesian.Ops[Signal, A] = v: Signal[A]
  }

  trait EventSyntax {
    implicit def eventSourceIsFunctor[A](v: EventSource[A]): Functor.Ops[Event, A] = v: Event[A]
    implicit def eventSourceIsApply[A](v: EventSource[A]): Apply.Ops[Event, A] = v: Event[A]
    implicit def eventSourceIsMonad[A](v: EventSource[A])(implicit eventIsMonad: FlatMap[Event]): FlatMapOps[Event, A] = v: Event[A]
    implicit def eventSourceIsCartesian[A](v: EventSource[A]): Cartesian.Ops[Event, A] = v: Event[A]
  }

  trait ReassignableEventSyntax {
    implicit def reassignableEventSourceIsFunctor[A](v: ReassignableEvent[A]): Functor.Ops[Event, A] = v: Event[A]
    implicit def reassignableEventSourceIsApply[A](v: ReassignableEvent[A]): Apply.Ops[Event, A] = v: Event[A]
    implicit def reassignableEventSourceIsMonad[A](v: ReassignableEvent[A])(implicit eventIsMonad: FlatMap[Event]): FlatMapOps[Event, A] = v: Event[A]
    implicit def reassignableEventSourceIsCartesian[A](v: ReassignableEvent[A]): Cartesian.Ops[Event, A] = v: Event[A]
  }

  object syntax extends AllSyntax with VarSyntax with EventSyntax with ReassignableEventSyntax with ReassignableSignalSyntax with FilterableSyntax {
    implicit def eventSourceIsMergeable[A](e: EventSource[A]): MergeableObs[Event, A] = new MergeableObs(e)
    implicit def eventSourceIsFilterable[A](e: EventSource[A]): FilterableObs[Event, A] = new FilterableObs(e)
    implicit def reassignableEventIsMergeable[A](e: ReassignableEvent[A]): MergeableObs[Event, A] = new MergeableObs(e)
    implicit def reassignableEventIsFilterable[A](e: ReassignableEvent[A]): FilterableObs[Event, A] = new FilterableObs(e)
  }

}