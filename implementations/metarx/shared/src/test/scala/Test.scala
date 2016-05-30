import org.scalatest.FlatSpec
import react.{ReactiveLibrary, ReactiveLibraryUsage}
import react.Rx.TestImplementation

class Test extends FlatSpec with TestImplementation {
  def reactLibrary_ = reactive.library
}
