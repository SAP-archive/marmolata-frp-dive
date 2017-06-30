package com.sap.marmolata.react.react.impls.selfrx.debugger

import scala.annotation.StaticAnnotation

class JSExport extends StaticAnnotation

trait JavaScriptInterface {
  self: Debugger =>
}

trait DebugSelfRxImplJavaScriptInterface {
  self: DebuggerSelfRxImpl =>
}