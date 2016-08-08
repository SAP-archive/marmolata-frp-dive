import react.core.{ReactiveDeclaration, ReactiveLibrary}
import react.core.ReactiveDeclaration
import react.impls.scalarx.ScalaRxImpl

package object reactive {
  object Impl extends ScalaRxImpl with ReactiveDeclaration

  val library: ReactiveLibrary with ReactiveDeclaration = Impl
}
