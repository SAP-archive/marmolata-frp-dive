import org.scalatest.FlatSpec
import react.core.ReactiveLibraryUsage
import react.core.tests.TestImplementation

class MetaRxTest extends FlatSpec with TestImplementation {
  def reactLibrary_ = reactive.library
  override def shouldRunPropertyTests: Boolean = false
}
