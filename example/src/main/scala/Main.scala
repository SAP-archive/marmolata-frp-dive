import cats.Apply
import com.sap.marmolata.react.api.ReactiveLibrary
import org.scalajs.dom
import org.scalajs.dom.Element
import com.sap.marmolata.react.react.debug.{AnnotateStack, AnnotateStackAnnotation}
import com.sap.marmolata.react.react.impls.selfrx.debugger.{Debugger, DebuggerSelfRxImpl}
import com.sap.marmolata.react.react.impls.selfrx.debugger.updatelogger.{HasHistoryLogger, LogSelfrxImpl, RecordingLogUpdates}
import com.sap.marmolata.react.react.impls.selfrx.debugger.visualization.{NamedGraphNodeAnnotation, ReactiveDebugger, SourceMapConsumerForFile}
import com.sap.marmolata.react.react.impls.selfrx.debugger.Debugger

import scala.scalajs.js.annotation.{JSExport, JSName}
import reactive.library._
import reactive.library.syntax._

trait Renderable {
  val domNode: dom.Element

  final def renderTopLevel(id: String): Unit = {
    dom.document.getElementById(id).appendChild(domNode)
  }

  final def above(other: Renderable): Renderable = Vertical(this, other)
}

object ObjectTag extends AnnotateStackAnnotation {
  override def description: String = "Renderable"
}

object EditTag extends Annotation {
  override def description: String = "EditBox"
  override def parent: Option[Annotation] = Some(ObjectTag)
}

object LabelTag extends Annotation {
  override def description: String = "Label"
  override def parent: Option[Annotation] = Some(ObjectTag)
}

case class Label(initialValue: String = "") extends Renderable {
  val domNode: dom.Element = dom.document.createElement("span")
  val value: ReassignableVar[String] = ReassignableVar(initialValue)
  value.observe(x => domNode.textContent = x)
  value.tag(LabelTag)
}

case class EditBox(initialValue: String = "") extends Renderable {
  val domNode: dom.Element = dom.document.createElement("input")
  domNode.setAttribute("type", "text")

  val value: Var[String] = Var(initialValue).tag(EditTag)
}

case class Button() extends Renderable {
  val domNode: dom.Element = dom.document.createElement("input")
  domNode.setAttribute("type", "button")
  val clicks: EventSource[Unit] = EventSource[Unit]
  domNode.addEventListener(`type` = "click", (x: dom.raw.Event) => { clicks emit Unit; true })
}

class ReactiveDebugger0 extends Renderable {
  private val debugger: Debugger = reactive.library.asInstanceOf[DebuggerSelfRxImpl].debugger
  private val stackTrace: AnnotateStack = reactive.library.asInstanceOf[AnnotateStack]
  private val historyLogger: RecordingLogUpdates = reactive.library.asInstanceOf[HasHistoryLogger].historyLogger

  val reactiveDebugger: ReactiveDebugger =
    new ReactiveDebugger(debugger, stackTrace, new SourceMapConsumerForFile("target/scala-2.11/reactive-example-fastopt.js.map"), historyLogger)

  override val domNode: Element = {
    val result = dom.document.createElement("div")
    reactiveDebugger.renderAt(result)
    result
  }
}

case class Vertical(rs: Renderable*) extends Renderable {
  val domNode: Element = dom.document.createElement("div")
  rs.foreach { ele => domNode.appendChild(ele.domNode) }
}

@JSExport("MainR")
object Main {
  @JSExport
  def createMain(): Unit = {
    val l1 = Label("hallo")
    val l2 = Label("welt")
    val e3 = EditBox("")
    val b = Button()
    val l3 = Label("")
    e3.value.tag(NamedGraphNodeAnnotation("hallo"))

    val counter = b.clicks.count

    l3.value subscribe implicitly[Apply[Signal]].map4(l1.value, l2.value, e3.value, counter)((x, y, z, w) => s"$x $y $z $w")

    val layout = l1 above l2 above e3 above l3 above b above new ReactiveDebugger0()
    layout.renderTopLevel("com.sap.marmolata.react.react")
  }
}
