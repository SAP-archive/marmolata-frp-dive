package react.impls.selfrx.selfrx.tests

import org.scalatest.{FlatSpec, Matchers}
import react.core.ReactiveDeclaration
import react.core.tests.DefaultTests

class DebugSelfrxTest extends FlatSpec with DefaultTests {
  override def reactiveLibrary_ : ReactiveDeclaration = reactive.library
}
