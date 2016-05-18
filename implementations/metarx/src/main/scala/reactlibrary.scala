import react.impls.MetaRxImpl
import react.{ReactiveLibraryUsage, ReactiveLibrary}

package object reactive {
  private object


  Impl extends MetaRxImpl with ReactiveLibraryUsage

  val library: ReactiveLibrary with ReactiveLibraryUsage = Impl
}