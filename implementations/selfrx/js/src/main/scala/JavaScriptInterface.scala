package react.selfrx.debugger

import org.scalajs.dom
import react.selfrx.debugger.Debugger.{GraphNode, SingleNode, NodeDescription}
import reactive.selfrx.Primitive

import scala.collection.immutable.HashMap
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import facades.vis


object JavaScriptFunctions {
  case class NodeProperties(label: String, color: String, title: Option[String])

  def drawGraph(container: dom.Node, primitives: Seq[Primitive],
                nodeProperties: NodeDescription => NodeProperties,
                options: js.Object = js.Dynamic.literal().asInstanceOf[js.Object]): vis.Network = {
    val graph = Debugger.calculateGraph(primitives)

    val data = vis.NetworkData(
      graph.nodes.map(x => {
        val props = nodeProperties(x.node)
        new vis.GraphNode(x.id, props.label, props.title.map(js.UndefOr.any2undefOrA).getOrElse(js.undefined), props.color)
      }),
      graph.edges.map(x => new vis.GraphEdge(x.from, x.to))
    )

    val nodeHashMap: HashMap[String, Primitive] = HashMap(graph.nodes.collect { case GraphNode(id, SingleNode(p)) => (id, p) }: _*)

    val result = new vis.Network(container, data, options)
    result.on("click", p => { dom.console.log(p) })
    result
  }
}


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
}

trait DebugSelfRxImplJavaScriptInterface {
  this: DebuggerSelfRxImpl =>

  @JSExport("debugger")
  final def _debugger: Debugger = debugger
}
