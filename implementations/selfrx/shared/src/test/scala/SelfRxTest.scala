package react.impls.selfrx.tests

import org.scalatest.{Matchers, FlatSpec}
import react.core.tests.{PropertyTests, DefaultTests}
import react.core.ReactiveObject

class SelfRxTest extends DefaultTests with PropertyTests {
  private lazy val reactive: ReactiveObject = new react.impls.selfrx.ReactiveObject()
  lazy val reactiveLibrary_ = reactive.library
}
