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
  def drawGraph(container: dom.Node): vis.Network = {
    val graph = currentGraph()

    val data = NetworkData(
      graph.nodes.map(x => new GraphNode(x.id, x.label)),
      graph.edges.map(x => new GraphEdge(x.from, x.to))
    )

    new vis.Network(container, data)
  }
}

trait DebugSelfRxImplJavaScriptInterface {
  this: DebuggerSelfRxImpl =>

  @JSExport("debugger")
  final def _debugger: Debugger = debugger
}
