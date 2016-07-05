package react.selfrx.debugger

import org.scalajs.dom
import react.selfrx.debugger.Debugger.{PrimitiveGroup, GraphNode, SingleNode, NodeDescription}
import reactive.selfrx.Primitive

import scala.collection.immutable.HashMap
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import react.selfrx.debugger.facades.vis


object JavaScriptFunctions {
  class Network(val networkFacade: vis.Network, keyMap: HashMap[String, Primitive]) {
    def onNodeSelect(nodeCallback: Primitive => Unit): Unit = {
      networkFacade.on("selectNode", (obj: js.Any) => {
        keyMap.get(obj.asInstanceOf[js.Dynamic].nodes.asInstanceOf[js.Array[String]](0)).map {
          x => nodeCallback(x)
        }
        Unit: Unit
      })
    }
  }

  class NodeProperties(val grahNode: String => vis.GraphNode)

  object NodeProperties {
    def apply(label: String, color: String, title: Option[String]): NodeProperties = {
      new NodeProperties ((id: String) =>
        new vis.GraphNode(id, label, title.map(x => x: js.UndefOr[String]).getOrElse(js.undefined), color)
      )
    }

    def coloredNode(label: String, colors: List[String]) = {
      new NodeProperties((id: String) => vis.GraphNode.coloredNode(id, label, colors))
    }
  }


  def drawGraph(container: dom.Node, primitives: Seq[PrimitiveGroup],
                nodeProperties: NodeDescription => NodeProperties,
                options: js.Object = js.Dynamic.literal().asInstanceOf[js.Object]): Network = {
    val graph = Debugger.calculateGraph(primitives)

    val data = vis.NetworkData(
      graph.nodes.map(x => {
        val props = nodeProperties(x.node)
        props.grahNode(x.id)
      }),
      graph.edges.map(x => new vis.GraphEdge(x.from, x.to))
    )

    val nodeHashMap: HashMap[String, Primitive] = HashMap(graph.nodes.collect { case GraphNode(id, SingleNode(p)) => (id, p) }: _*)

    val result = new vis.Network(container, data, options)
    new Network(result, nodeHashMap)
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
