package react.impls.selfrx.tests

import org.scalatest.{Matchers, FlatSpec}
import react.core.tests.{ReactLibraryTests, TestConfiguration,TestImplementation}
import react.core.{ReactiveObject, ReactiveDeclaration}
import react.impls.selfrx.SelfRxImpl

class SelfRxTest extends FlatSpec with TestImplementation {
  private lazy val reactive: ReactiveObject = new react.impls.selfrx.ReactiveObject()
  lazy val reactLibrary_ = reactive.library
  override def shouldRunPropertyTests: Boolean = true

  override def testConfiguration: TestConfiguration =
    super.testConfiguration.copy(
      forwardExceptions = false
    )
}
