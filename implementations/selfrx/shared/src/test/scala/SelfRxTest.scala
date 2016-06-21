import org.scalatest.FlatSpec
import react.{ReactiveLibrary, ReactiveLibraryUsage}
import react.Rx.TestImplementation
import reactive.selfrx.SelfRxImpl

class SelfRxTest extends FlatSpec with TestImplementation {
  lazy val reactLibrary_ = new SelfRxImpl with ReactiveLibraryUsage
  override def shouldRunPropertyTests: Boolean = true
}
