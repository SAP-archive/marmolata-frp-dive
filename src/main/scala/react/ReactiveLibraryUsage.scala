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
  }
}
