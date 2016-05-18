package react

import scala.concurrent.{ExecutionContext, Future}

trait ReactiveLibraryUsage {
  self: ReactiveLibrary =>

  implicit final class FutureExtensions[A](f: Future[A]) {
    def toEvent(implicit ec: ExecutionContext): Event[A] = futureToEvent(f)
  }

  implicit final class SignalExtensions[A](s: Signal[A]) {
    def toEvent: Event[A] = self.toEvent(s)
  }

  implicit final class EventExtensions[A](event: Event[A]) {
    def toSignal(init: A) = self.toSignal(init, event)
    def toSignal: Signal[Option[A]] = event.map(Some(_): Option[A]).toSignal(None)
  }

  class ReassignableSignal[A](private[ReactiveLibraryUsage] val constr: Var[Signal[A]]) {
    def := (p: Signal[A]) = constr := p
    def := (p: A) = constr := Const(p)
  }

  implicit def reassignableSignalToSignal[A](p: ReassignableSignal[A]): Signal[A] =
    p.constr.flatMap(identity)

  object ReassignableSignal {
    def apply[A](init: Signal[A]) = new ReassignableSignal(Var(init))
    def apply[A](init: A) = new ReassignableSignal[A](Var(Const(init)))
  }

  class ReassignableEvent[A](private[ReactiveLibraryUsage] val constr: Var[Event[A]]) {
    def := (p: Event[A]) = constr := p
  }

  object ReassignableEvent {
    def apply[A](): ReassignableEvent[A] = new ReassignableEvent(Var(Event()))
  }

  implicit def reassignableEventToEvent[A](p: ReassignableEvent[A]): Event[A] =
    p.constr.flatMap(_.toSignal).toEvent.mapPartial { case Some(x) => x }

  implicit class ListCombinators[T](seq: List[Signal[T]]) {
    def sequence: Signal[List[T]] = {
      val zeroChannel: Signal[List[T]] = Var[List[T]](List.empty[T])
      seq.foldLeft(zeroChannel) {
        case (acc, readChannel) => acc.zipWith(readChannel) {
          case (list, value) => list :+ value
        }
      }
    }
  }
}
