package react.debug

import react.ReactiveLibrary.Nameable

trait StrictMap extends DebugLayer {
  override def newSignal[A](u: underlying.Signal[A]): Signal[A] = {
    u.observe(_ => {})
    super.newSignal[A](u)
  }

  override def newEvent[A](u: underlying.Event[A]): Event[A] = {
    u.observe(_ => {})
    super.newEvent[A](u)
  }
}
