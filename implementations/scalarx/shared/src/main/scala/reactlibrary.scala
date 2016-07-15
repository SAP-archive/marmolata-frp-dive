import react.impls.ScalaRxImpl
import react.{ReactiveDeclaration, ReactiveLibraryUsage, ReactiveLibrary}

package object reactive {
  object Impl extends ScalaRxImpl with ReactiveDeclaration

  val library: ReactiveLibrary with ReactiveDeclaration = Impl
}
