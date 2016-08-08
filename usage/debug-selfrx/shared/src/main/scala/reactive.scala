import react.core.ReactiveDeclaration
import react.debug.{StrictMap, DebugLayer, AnnotateStack}
import react.impls.selfrx.SelfRxImpl
import react.impls.selfrx.debugger.DebuggerSelfRxImpl
import react.impls.selfrx.debugger.updatelogger.{HasHistoryLogger, RecordingLogUpdates, LogSelfrxImpl}

package object reactive {
  val library: ReactiveDeclaration = {
    val _underlying = new LogSelfrxImpl {}
    new DebugLayer(_underlying) with AnnotateStack with DebuggerSelfRxImpl with StrictMap with ReactiveDeclaration with HasHistoryLogger {
      override val historyLogger: RecordingLogUpdates = _underlying.historyLogger
    }
  }
}
