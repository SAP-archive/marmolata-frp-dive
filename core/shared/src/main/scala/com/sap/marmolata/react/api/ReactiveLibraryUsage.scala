package com.sap.marmolata.react.api

import cats._
import cats.syntax.all._
import cats.syntax.AllSyntax
import com.sap.marmolata.react.api.ReactiveLibrary.{Cancelable, Observable}
import com.sap.marmolata.react.react.core.ReactiveLibrary.{Cancelable, Observable}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait ReactiveDeclaration extends ReactiveLibrary with ReactiveLibraryUsage

trait ReactiveObject {
  val library: ReactiveDeclaration
}

trait ReactiveLibraryUsage extends ReactiveLibraryUsageTime {
  self: ReactiveLibrary =>

  implicit final class FutureExtensions[A](f: Future[A]) {
    @deprecated("use [[FutureExtensions2#executeFuture]] instead", "0.33")
    def toEvent(implicit ec: ExecutionContext): Event[A] = futureToEvent(f)
  }

  implicit final class FutureExtensions2[A](s: Observable[Future[A]]) {
    def executeFuture(defaultVal: A)(implicit ec: ExecutionContext): Signal[A] = {
      val result = Var(defaultVal)
      var currentId = 0
      var lastId = 0
      s.observe { f =>
        currentId += 1
        val thisId = currentId
        f.map { res =>
          if (currentId > lastId) {
            lastId = thisId
            result := res
          }
        }
      }
      result
    }
  }

  implicit final class SignalExtensions[A](s: Signal[A]) {
    /**
      * convert this signal into an event
      *
      * returns an event that triggers each time the value of the signal changes
      * Note, that {{{ x.toSignal.toEvent !== Function.identity }}} is because
      * this only returns distinct events
      */
    def toEvent: Event[A] = self.toEvent(s)

    def toEvents[B](f: A => Event[B]): Event[B] = flattenEvents(marmolataDiveSignalTypeclass.map(s)(f))

    /**
      * returns a new Event that triggers each time e is triggered with the value of f]
      *
      * @param e the event at which points in time the returned event triggers
      * @param f the function with the value of the event. For each triggered event, f is called with
      *          parameters of the current values of this signal and the event e
      */
    def triggerWhen[B, C](e: Event[B], f: (A, B) => C): Event[C] = self.triggerWhen(s, e, f)

    /**
      * returns a new Event that triggers each time @p e is triggered with the value of this signal at that point in time
      */
    def triggerWhen[B](e: Event[B]): Event[A] = triggerWhen[B, A](e, (x, _) => x)

    /**
      * returns a signal that is changed only at specific points in time
      *
      * @param e points in time when the returned signal is updated to the new value
      */
    def changeWhen(e: Event[Any]): Signal[A] = triggerWhen(e).toSignal(s.now)

    /**
      * like TODO: Signal#observe but only trigger after the first change of this signal
      */
    def observeLater(f: A => Unit): Cancelable = {
      s.toEvent.observe { x =>
        if (!f.isInstanceOf[PartialFunction[_, _]] || f.asInstanceOf[PartialFunction[A, Unit]].isDefinedAt(x)) {
          f(x)
        }
        else {
          println("[warn] This usage of observeLater is deprecated and will be removed in a later version. " +
            "Please specify a total function instead of a partial function")
        }
      }
    }

    def toTry: Signal[Try[A]] = signalToTry(s)
  }

  implicit final class EventExtensions[A](event: Event[A]) {
    def toSignal(init: A): Signal[A] = self.toSignal(init, event)
    def toSignal: Signal[Option[A]] = event.map(Some(_): Option[A]).toSignal(None)
    def triggerWhen[B, C](s: Signal[B], f: (A, B) => C): Event[C] = self.triggerWhen(s, event, (a: B, b: A) => f(b, a))
    def or(e: Event[Any]): Event[Unit] = {
      implicitly[Mergeable[Event]].merge(event.map(Function.const(Unit)), e.map(Function.const(Unit)))
    }

    def mergeEither[B](e: Event[B]): Event[Either[A, B]] = {
      implicitly[Mergeable[Event]].merge(event.map(Left(_)), e.map(Right(_)))
    }

    def fold[B](init: B)(foldFun: (A, B) => B): Signal[B] = {
      self.fold(event, init, foldFun)
    }

    def count(): Signal[Int] = {
      fold(0) { (x, y) => y + 1 }
    }
  }

  implicit final class SignalEventExtensions[A](s: Signal[Event[A]]) {
    def flatten(): Event[A] = flattenEvents(s)
  }

  class ReassignableEvent[A](private[ReactiveLibraryUsage] val constr: Var[Event[A]]) {
    def subscribe(p: Event[A]): Unit = constr := p
  }

  object ReassignableEvent {
    def apply[A](): ReassignableEvent[A] = new ReassignableEvent(Var(EventSource()))
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


  implicit def reassignableSignalToSignal[A](p: ReassignableVar[A]): Signal[A] = p.toSignal

  implicit def reassignableEventToEvent[A](p: ReassignableEvent[A]): Event[A] = {
    import self.unsafeImplicits.{marmolataDiveSignalTypeclass => s}
    import syntax._
    (p.constr: Signal[Event[A]]).flatMap(_.toSignal).toEvent.mapPartial { case Some(x) => x }
  }

  trait VarSyntax {
    implicit def varIsFunctor[A](v: Var[A]): Functor.Ops[Signal, A] = v: Signal[A]
    implicit def varIsApply[A](v: Var[A]): Apply.Ops[Signal, A] = v: Signal[A]
    implicit def varIsFlatMap[A](v: Var[A])(implicit signalIsMonad: FlatMap[Signal]): FlatMap.Ops[Signal, A] = v: Signal[A]
    implicit def varIsCartesian[A](v: Var[A]): Cartesian.Ops[Signal, A] = v: Signal[A]
    implicit def varIsSignalExtensions[A](v: Var[A]): SignalExtensions[A] = v: Signal[A]
  }

  trait ReassignableVarSyntax {
    implicit def reassignableVarIsFunctor[A](v: ReassignableVar[A]): Functor.Ops[Signal, A] = v: Signal[A]
    implicit def reassignableVarIsApply[A](v: ReassignableVar[A]): Apply.Ops[Signal, A] = v: Signal[A]
    implicit def reassignableVarIsFlatMap[A](v: ReassignableVar[A])(implicit signalIsMonad: FlatMap[Signal]): FlatMap.Ops[Signal, A] = v: Signal[A]
    implicit def reassignableVarIsCartesian[A](v: ReassignableVar[A]): Cartesian.Ops[Signal, A] = v: Signal[A]
    implicit def reassignableVarIsSignalExtensions[A](v: ReassignableVar[A]): SignalExtensions[A] = v: Signal[A]
  }

  trait EventSyntax {
    implicit def eventSourceIsFunctor[A](v: EventSource[A]): Functor.Ops[Event, A] = v: Event[A]
    implicit def eventSourceIsApply[A](v: EventSource[A])(implicit eventIsApply: Apply[Event]): Apply.Ops[Event, A] = v: Event[A]
    implicit def eventSourceIsMonad[A](v: EventSource[A])(implicit eventIsMonad: FlatMap[Event]): FlatMap.Ops[Event, A] = v: Event[A]
    implicit def eventSourceIsCartesian[A](v: EventSource[A])(implicit eventIsCartesian: Cartesian[Event]): Cartesian.Ops[Event, A] = v: Event[A]
    implicit def eventSourceIsEventExtensions[A](v: EventSource[A]): EventExtensions[A] = v: Event[A]
  }

  trait ReassignableEventSyntax {
    implicit def reassignableEventSourceIsFunctor[A](v: ReassignableEvent[A]): Functor.Ops[Event, A] = v: Event[A]
    implicit def reassignableEventSourceIsApply[A](v: ReassignableEvent[A])(implicit eventIsApply: Apply[Event]): Apply.Ops[Event, A] = v: Event[A]
    implicit def reassignableEventSourceIsMonad[A](v: ReassignableEvent[A])(implicit eventIsMonad: FlatMap[Event]): FlatMap.Ops[Event, A] = v: Event[A]
    implicit def reassignableEventSourceIsCartesian[A](v: ReassignableEvent[A])(implicit eventIsCartesian: Cartesian[Event]): Cartesian.Ops[Event, A] =
      v: Event[A]
    implicit def reassignableEventSourceIsEventExtensions[A](v: ReassignableEvent[A]): EventExtensions[A] = v: Event[A]
  }

  trait Syntax extends AllSyntax with VarSyntax with EventSyntax with ReassignableEventSyntax with ReassignableVarSyntax with FilterableSyntax {
    implicit def eventSourceIsMergeable[A](e: EventSource[A]): MergeableObs[Event, A] = new MergeableObs(e)
    implicit def eventSourceIsFilterable[A](e: EventSource[A]): FilterableObs[Event, A] = new FilterableObs(e)
    implicit def reassignableEventIsMergeable[A](e: ReassignableEvent[A]): MergeableObs[Event, A] = new MergeableObs(e)
    implicit def reassignableEventIsFilterable[A](e: ReassignableEvent[A]): FilterableObs[Event, A] = new FilterableObs(e)

    // this is only to make IntelliJ happy:
    implicit def varIsSignal[A](v: Var[A]): Signal[A] = v
    implicit def eventSourceIsEvent[A](e: EventSource[A]): Event[A] = e
  }

  object syntax extends Syntax
}
