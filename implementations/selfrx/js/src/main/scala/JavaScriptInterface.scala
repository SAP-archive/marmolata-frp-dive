package react.selfrx.debugger

import org.scalajs.dom
import react.selfrx.debugger.facades.vis.{GraphEdge, GraphNode, NetworkData}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import facades.vis


trait JavaScriptInterface {
  self: Debugger =>
  @JSExport
  def printGraph(): Unit = logInConsle()

  @JSExport
  def dotGraphAsString(): String = {
    var s : String = ""
    drawCurrent { w => s += w }
    s
  }

  @JSExport
  def drawGraph(container: dom.Node, filter: String => Option[String], options: js.Object = js.Dynamic.literal().asInstanceOf[js.Object]): vis.Network = {
    val graph = currentGraph(filter)

    val data = NetworkData(
      graph.nodes.map(x => new GraphNode(x.id, x.label, x.detailed, Debugger.colorOfNode(x.typeOfNode))),
      graph.edges.map(x => new GraphEdge(x.from, x.to))
    )

    new vis.Network(container, data, options)
  }
}

trait DebugSelfRxImplJavaScriptInterface {
  this: DebuggerSelfRxImpl =>

  @JSExport("debugger")
  final def _debugger: Debugger = debugger
}
