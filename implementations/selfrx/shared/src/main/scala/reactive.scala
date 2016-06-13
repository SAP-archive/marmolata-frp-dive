import react.{ReactiveLibrary, ReactiveLibraryUsage}
import reactive.selfrx.{SelfRxLogging, SelfRxImpl}

package object reactive {
  object debugger extends react.selfrx.debugger.Debugger

  object Impl extends SelfRxImpl with ReactiveLibraryUsage {
    override val logger: SelfRxLogging = debugger
  }

  val library: ReactiveLibrary with ReactiveLibraryUsage = Impl
}