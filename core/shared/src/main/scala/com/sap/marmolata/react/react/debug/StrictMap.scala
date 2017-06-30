package com.sap.marmolata.react.react.debug

import com.sap.marmolata.react.api.ReactiveLibrary

object InternalStrictMap extends Annotation {
  override def parent: Option[Annotation] = Some(InternalAnnotation)
  override def description: String = "internal.strictMap"
}

trait StrictMap extends DebugLayer {
  override def newSignal[A](u: underlying.Signal[A]): Signal[A] = {
    val obs = u.observe(_ => {})
    obs.tag(InternalStrictMap)
    super.newSignal[A](u)
  }

  override def newEvent[A](u: underlying.Event[A]): Event[A] = {
    val obs = u.observe(_ => {})
    obs.tag(InternalStrictMap)
    super.newEvent[A](u)
  }
}
