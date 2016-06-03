package react.impls.helper

import react.ReactiveLibrary
import react.ReactiveLibrary.{SignalCompanionObject, EventCompanionObject, EventSourceCompanionObject, Cancelable}


object NonCancelable extends Cancelable {
  override def kill(): Unit = {}

  import language.implicitConversions
  implicit def unitToCancelable(x: Unit): Cancelable = NonCancelable
}

trait DefaultSignalObject {
  self: ReactiveLibrary =>

  final object Signal extends SignalCompanionObject[Signal] {
    override def Const[A](value: A): Signal[A] = Var(value)
  }
}

trait DefaultEventObject {
  self: ReactiveLibrary =>

  final object Event extends EventCompanionObject[Event] {
    override def Never: Event[Nothing] = EventSource[Nothing]
  }
}

trait ReactiveLibraryImplementationHelper {
  self: ReactiveLibrary =>
  override protected type VolatileHelper = Any
}
