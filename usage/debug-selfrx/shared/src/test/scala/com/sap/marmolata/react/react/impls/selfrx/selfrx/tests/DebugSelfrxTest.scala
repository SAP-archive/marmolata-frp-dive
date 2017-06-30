package com.sap.marmolata.react.react.impls.selfrx.selfrx.tests

import com.sap.marmolata.react.api.ReactiveDeclaration
import com.sap.marmolata.react.api.tests.DefaultTests
import org.scalatest.{FlatSpec, Matchers}
import com.sap.marmolata.react.react.core.ReactiveDeclaration

class DebugSelfrxTest extends FlatSpec with DefaultTests {
  override def reactiveLibrary_ : ReactiveDeclaration = reactive.library
}
