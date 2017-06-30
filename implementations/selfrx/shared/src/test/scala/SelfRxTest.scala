package com.sap.marmolata.react.react.impls.selfrx.tests

import com.sap.marmolata.react.api.ReactiveObject
import com.sap.marmolata.react.api.tests.DefaultTests
import org.scalatest.{FlatSpec, Matchers}
import com.sap.marmolata.react.react.core.tests.{DefaultTests, PropertyTests}

class SelfRxTest extends DefaultTests /*with PropertyTests*/ {
  private lazy val reactive: ReactiveObject = new com.sap.marmolata.react.react.impls.selfrx.ReactiveObject()
  lazy val reactiveLibrary_ = reactive.library
}
