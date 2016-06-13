package reactive

import scala.scalajs.js.annotation.JSExport


@JSExport("ReactiveDebbuger")
object JavaScriptInterface {
  @JSExport
  def printGraph(): Unit = debugger.logInConsle()
}

