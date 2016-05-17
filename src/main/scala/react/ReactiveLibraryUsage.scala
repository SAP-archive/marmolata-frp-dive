package react

import scala.concurrent.{ExecutionContext, Future}

trait ReactiveLibraryUsage {
  self: ReactiveLibrary =>
  implicit final class FutureExtensions[A](f: Future[A]) {
    def event(implicit ec: ExecutionContext): Event[A] = futureToEvent(f)
  }

  implicit final class SignalExtensions[A](s: Signal[A]) {
    def event: Event[A] = toEvent(s)
  }

  implicit final class EventExtensions[A](event: Event[A]) {
    def signal(init: A) = toSignal(init, event)
  }
}
