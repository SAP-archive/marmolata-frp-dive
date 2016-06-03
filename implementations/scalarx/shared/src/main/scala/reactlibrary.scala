import react.impls.ScalaRxImpl
import react.{ReactiveLibraryUsage, ReactiveLibrary}

package object reactive {
  object Impl extends ScalaRxImpl with ReactiveLibraryUsage

  val library: ReactiveLibrary with ReactiveLibraryUsage = Impl
}
