import react.core.ReactiveDeclaration
import react.impls.metarx.MetaRxImpl

package object reactive {
  private object


  Impl extends MetaRxImpl with ReactiveDeclaration

  val library: ReactiveDeclaration = Impl
}
