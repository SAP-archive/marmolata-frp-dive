package react.impls.helper

import react.ReactiveLibrary.Cancelable


object NonCancelable extends Cancelable {
  override def kill(): Unit = {}

  implicit def unitToCancelable(x: Unit): Cancelable = NonCancelable
}