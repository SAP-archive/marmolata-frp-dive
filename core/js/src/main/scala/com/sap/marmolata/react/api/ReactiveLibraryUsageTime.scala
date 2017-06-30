package com.sap.marmolata.react.api

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js.timers._

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
