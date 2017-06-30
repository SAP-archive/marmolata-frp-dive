import com.sap.marmolata.react.api.ReactiveDeclaration
import com.sap.marmolata.react.react.debug.{AnnotateStack, DebugLayer, StrictMap}
import com.sap.marmolata.react.react.impls.selfrx.SelfRxImpl
import com.sap.marmolata.react.react.impls.selfrx.debugger.DebuggerSelfRxImpl
import com.sap.marmolata.react.react.impls.selfrx.debugger.updatelogger.{HasHistoryLogger, LogSelfrxImpl, RecordingLogUpdates}

package object reactive {
  val library: ReactiveDeclaration = {
    val _underlying = new LogSelfrxImpl {}
    new DebugLayer(_underlying) with AnnotateStack with DebuggerSelfRxImpl with StrictMap with ReactiveDeclaration with HasHistoryLogger {
      override val historyLogger: RecordingLogUpdates = _underlying.historyLogger
    }
  }
}
