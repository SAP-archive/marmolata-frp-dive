import com.sap.marmolata.react.api.ReactiveDeclaration
import com.sap.marmolata.react.react.impls.metarx.MetaRxImpl

package object reactive {
  private object


  Impl extends MetaRxImpl with ReactiveDeclaration

  val library: ReactiveDeclaration = Impl
}
