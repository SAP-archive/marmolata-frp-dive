import react.ReactiveLibrary.Nameable
import react.debug.HasUnderlying
import react.{ReactiveLibrary, ReactiveLibraryUsage}
import react.logged.AnnotateStack
import react.selfrx.debugger.{Debugger, RecordedSelfRxImpl}
import reactive.selfrx.Primitive

import scala.scalajs.js.annotation.JSExport

package object reactive {
  private val recorded = new RecordedSelfRxImpl()

  @JSExport("ReactiveDebugger")
  val debugger = new Debugger()

  private val logged = new AnnotateStack(recorded) with ReactiveLibraryUsage {
    override def onNew(u: HasUnderlying[Nameable]): Unit = {
      super.onNew(u)
      u.under match {
        case p: Primitive =>
          debugger.createPrimitive(p)
        case _ =>
      }
    }
  }

  val library: ReactiveLibrary with ReactiveLibraryUsage = logged
}