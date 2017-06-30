package com.sap.marmolata.react.api

import scala.scalajs.js

trait EnsureLargeEnoughStackTrace {
  if (js.Error.asInstanceOf[js.Dynamic].stackTraceLimit.isInstanceOf[Int]) {
    val errorDynamic = js.Error.asInstanceOf[js.Dynamic]
    if (errorDynamic.stackTraceLimit.asInstanceOf[Int] < 80) {
      errorDynamic.stackTraceLimit = 80
    }
  }
}
