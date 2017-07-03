package com.sap.marmolata

import com.sap.marmolata.react.api.{ReactiveDeclaration, ReactiveLibrary}
import com.sap.marmolata.react.impls.scalarx.ScalaRxImpl

package object react {
  object Impl extends ScalaRxImpl with ReactiveDeclaration

  val library: ReactiveLibrary with ReactiveDeclaration = Impl
}
