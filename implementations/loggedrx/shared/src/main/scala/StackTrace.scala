package com.sap.marmolata.react.react.logged

import com.sap.marmolata.react.react.core.ReactiveLibrary.Nameable
import com.sap.marmolata.react.react.debug.{HasUnderlying, DebugLayer}

class AnnotateStack(underlying: com.sap.marmolata.react.react.ReactiveLibrary) extends DebugLayer(underlying) {
  override def onNew(u: HasUnderlying[Nameable]): Unit = {
    super.onNew(u)
    val currentStackTrace = new RuntimeException().getStackTrace
    val stackframe = currentStackTrace.collectFirst {
      case st if List("cats",
          "com.sap.marmolata.react.react",
          "reactive"
        ).forall(!st.getClassName.startsWith(_)) =>
        st.toString
    }.getOrElse("empty stack trace - your browser may not be supported")
    u.under.name += stackframe
  }

  override def implementationName: String = s"annotate-stack-of-${super.implementationName}"
}