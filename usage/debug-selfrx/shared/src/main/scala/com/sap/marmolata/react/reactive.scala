package com.sap.marmolata

import com.sap.marmolata.react.api.ReactiveDeclaration
import com.sap.marmolata.react.debug.{AnnotateStack, DebugLayer, StrictMap}
import com.sap.marmolata.react.impls.selfrx.debugger.DebuggerSelfRxImpl
import com.sap.marmolata.react.impls.selfrx.debugger.updatelogger.{HasHistoryLogger, LogSelfrxImpl, RecordingLogUpdates}

package object react {
  val library: ReactiveDeclaration = {
    val _underlying = new LogSelfrxImpl {}
    new DebugLayer(_underlying) with AnnotateStack with DebuggerSelfRxImpl with StrictMap with ReactiveDeclaration with HasHistoryLogger {
      override val historyLogger: RecordingLogUpdates = _underlying.historyLogger
    }
  }
}
