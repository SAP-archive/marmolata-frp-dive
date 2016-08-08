package react.impls.metarx.tests

import react.core.tests.DefaultTests

class MetaRxTest extends DefaultTests {
  def reactLibrary_ = reactive.library
  override def shouldRunPropertyTests: Boolean = false
}
