package react

import react.{ReactiveLibrary}
import pl.metastack.metarx
import pl.metastack.metarx.{ReadStateChannel, ReadChannel}

//object MetaStackImpl extends ReactiveLibrary {
//  class EventSource[A](constr: ReadChannel[A])
//  class Signal[A](constr: ReadStateChannel[A])
//
//  override def toEvent[A](signal: Signal[A]): EventSource[A] = ???
//  override def toSignal[A](init: A, event: EventSource[A]): Signal[A] = ???
//
//  override type Var[A] = metarx.Var[A]
//  object Var extends VarCompanionObject[Var] {
//    override def apply[A](init: A): Var[A] = metarx.Var(init)
//  }
//}
