import org.scalatest.FlatSpec
import react.core.ReactiveLibraryUsage
import react.core.tests.TestImplementation

class ScalaRxTest extends FlatSpec with TestImplementation {
   def reactLibrary_ = reactive.library
   override def shouldRunPropertyTests: Boolean = false
}
