import react.ReactiveDeclaration
import react.debug.{StrictMap, DebugLayer, AnnotateStack}
import react.selfrx.debugger.DebuggerSelfRxImpl
import reactive.selfrx.SelfRxImpl

package object reactive {
  val library: react.ReactiveDeclaration = {
    val underlying = new SelfRxImpl {}
    new DebugLayer(underlying) with AnnotateStack with DebuggerSelfRxImpl with StrictMap with ReactiveDeclaration
  }
}
