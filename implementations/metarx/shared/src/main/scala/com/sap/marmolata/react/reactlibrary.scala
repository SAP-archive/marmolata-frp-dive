package com.sap.marmolata

import com.sap.marmolata.react.api.ReactiveDeclaration
import com.sap.marmolata.react.impls.metarx.MetaRxImpl

package object react {
  private object Impl extends MetaRxImpl with ReactiveDeclaration

  val library: ReactiveDeclaration = Impl
}
