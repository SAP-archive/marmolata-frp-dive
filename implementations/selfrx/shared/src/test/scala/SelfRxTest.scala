import org.scalatest.{Matchers, FlatSpec}
import react.LibTests.ReactLibraryTests
import react.{ReactiveDeclaration, ReactiveLibraryUsage}
import react.Rx.TestImplementation
import reactive.selfrx.SelfRxImpl

class SelfRxTest extends FlatSpec with TestImplementation {
  lazy val reactLibrary_ = new SelfRxImpl with ReactiveDeclaration

  // TODO: find out why ScalaTest crashes when we run the property tests
  override def shouldRunPropertyTests: Boolean = true
}
