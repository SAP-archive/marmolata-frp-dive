import scala.scalajs.js.annotation.JSExport

@JSExport("reactiveLibrary")
object  ReactiveJavascript {
  @JSExport("reactive")
  def useThisOnlyInJavascript: Any = reactive.library
}
