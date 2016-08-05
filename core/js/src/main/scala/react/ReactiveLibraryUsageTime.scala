package react

import scala.concurrent.duration.FiniteDuration
import scalajs.js.timers._

/**
  * Created by d066276 on 04.08.16.
  */
trait ReactiveLibraryUsageTime {
  self: ReactiveLibrary =>

  implicit class TimeEventExtensions[A](event: Event[A]) {
    def delayed(duration: FiniteDuration): Event[A] = {
      val result = EventSource[A]
      event.observe { value =>
        setTimeout(duration)(result emit value)
      }
      result
    }
  }

  implicit class TimeEventCompanionObjectExtensions(obj: Event.type) {
    def every(duration: FiniteDuration): Event[Unit] = {
      val result = EventSource[Unit]
      setInterval(duration)(result emit Unit)
      result
    }
  }


}
