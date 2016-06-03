import react.impls.MonixImpl
import react.{ReactiveLibraryUsage, ReactiveLibrary}

package object reactive {
  private object Impl extends MonixImpl with ReactiveLibraryUsage

  val library: ReactiveLibrary with ReactiveLibraryUsage = Impl
}
