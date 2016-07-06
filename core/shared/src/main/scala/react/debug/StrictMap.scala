package react.debug

import react.ReactiveLibrary.Nameable

trait StrictMap extends DebugLayer {
  override def newSignal[A](u: underlying.Signal[A]): Signal[A] = {
    val obs = u.observe(_ => {})
    obs.name = "internal.strictMap"
    super.newSignal[A](u)
  }

  override def newEvent[A](u: underlying.Event[A]): Event[A] = {
    val obs = u.observe(_ => {})
    obs.name = "internal.strictMap"
    super.newEvent[A](u)
  }
}
