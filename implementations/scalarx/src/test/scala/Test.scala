import react.{ReactiveLibrary, ReactiveLibraryUsage}
import react.Rx.TestImplementation

class Test extends TestImplementation {
  def impls = List(reactive.library)
}
