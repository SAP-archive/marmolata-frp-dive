import org.scalatest.FlatSpec
import react.{ReactiveLibrary, ReactiveLibraryUsage}
import react.Rx.TestImplementation

class MetaRxTest extends FlatSpec with TestImplementation {
  def reactLibrary_ = reactive.library
  override def shouldRunPropertyTests: Boolean = false
}
