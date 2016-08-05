import react.impls.MetaRxImpl
import react.{ReactiveDeclaration, ReactiveLibrary}

package object reactive {
  private object


  Impl extends MetaRxImpl with ReactiveDeclaration

  val library: ReactiveDeclaration = Impl
}
