package react.selfrx.tests

import org.scalatest.{FlatSpec, Matchers}
import react.Rx.TestImplementation
import react.{ReactiveDeclaration, ReactiveLibraryUsage, ReactiveLibrary}

class DebugSelfrxTest extends FlatSpec with TestImplementation {
  override def reactLibrary_ : ReactiveDeclaration = reactive.library
  override def shouldRunPropertyTests: Boolean = false
}
