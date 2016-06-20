import react.ReactiveDeclaration
import react.logged.{AnnotateStack}
import react.selfrx.debugger.DebuggerSelfRxImpl
import reactive.selfrx.SelfRxImpl

package object reactive {
  val library: react.ReactiveDeclaration = {
    val underlying = new SelfRxImpl {}
    new AnnotateStack(underlying) with DebuggerSelfRxImpl with ReactiveDeclaration
  }
}
