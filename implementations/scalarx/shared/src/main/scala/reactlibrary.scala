import com.sap.marmolata.react.api.{ReactiveDeclaration, ReactiveLibrary}
import com.sap.marmolata.react.react.core.{ReactiveDeclaration, ReactiveLibrary}
import com.sap.marmolata.react.react.impls.scalarx.ScalaRxImpl

package object reactive {
  object Impl extends ScalaRxImpl with ReactiveDeclaration

  val library: ReactiveLibrary with ReactiveDeclaration = Impl
}
