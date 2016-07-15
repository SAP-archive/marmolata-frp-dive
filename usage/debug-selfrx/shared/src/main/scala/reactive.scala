import react.ReactiveDeclaration
import react.debug.{StrictMap, DebugLayer, AnnotateStack}
import react.selfrx.debugger.DebuggerSelfRxImpl
import react.selfrx.debugger.updatelogger.{HasHistoryLogger, RecordingLogUpdates, LogSelfrxImpl}
import reactive.selfrx.SelfRxImpl

package object reactive {
  val library: react.ReactiveDeclaration = {
    val _underlying = new LogSelfrxImpl {}
    new DebugLayer(_underlying) with AnnotateStack with DebuggerSelfRxImpl with StrictMap with ReactiveDeclaration with HasHistoryLogger {
      override val historyLogger: RecordingLogUpdates = _underlying.historyLogger
    }
  }
}
