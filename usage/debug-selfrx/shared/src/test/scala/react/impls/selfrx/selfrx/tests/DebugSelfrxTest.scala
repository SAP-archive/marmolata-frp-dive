package react.impls.selfrx.selfrx.tests

import org.scalatest.{FlatSpec, Matchers}
import react.core.ReactiveDeclaration
import react.core.tests.TestImplementation

class DebugSelfrxTest extends FlatSpec with TestImplementation {
  override def reactLibrary_ : ReactiveDeclaration = reactive.library
  override def shouldRunPropertyTests: Boolean = false
}
