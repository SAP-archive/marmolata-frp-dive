package react.selfrx.tests

import org.scalatest.{FlatSpec, Matchers}
import react.Rx.TestImplementation
import react.{ReactiveLibraryUsage, ReactiveLibrary}

class DebugSelfrxTest extends FlatSpec with TestImplementation {
  override def reactLibrary_ : ReactiveLibrary with ReactiveLibraryUsage = reactive.library
}
