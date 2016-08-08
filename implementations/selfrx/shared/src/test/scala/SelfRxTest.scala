import org.scalatest.{Matchers, FlatSpec}
import react.LibTests.{ReactLibraryTests, TestConfiguration}
import react.Rx.TestImplementation
import react.core.ReactiveDeclaration
import react.impls.selfrx.SelfRxImpl

class SelfRxTest extends FlatSpec with TestImplementation {
  lazy val reactLibrary_ = new SelfRxImpl with ReactiveDeclaration
  override def shouldRunPropertyTests: Boolean = true

  override def testConfiguration: TestConfiguration =
    super.testConfiguration.copy(
      forwardExceptions = false
    )
}
