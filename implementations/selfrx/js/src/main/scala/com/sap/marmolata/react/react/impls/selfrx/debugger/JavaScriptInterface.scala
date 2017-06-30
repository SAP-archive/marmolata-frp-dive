package com.sap.marmolata.react.react.impls.selfrx.debugger

import org.scalajs.dom
import com.sap.marmolata.react.react.impls.selfrx.debugger.Debugger.{GraphNode, NodeDescription, PrimitiveGroup}
import com.sap.marmolata.react.react.impls.selfrx.debugger.facades.vis

import scala.collection.immutable.HashMap
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSExport


object JavaScriptFunctions {
  class NodeProperties(val graphNode: String => vis.GraphNode)

  object NodeProperties {
    def apply(label: String, color: String, title: Option[String]): NodeProperties = {
      new NodeProperties ((id: String) =>
        new vis.GraphNode(id, label, title.orUndefined, color)
      )
    }

    def coloredNode(label: String, colors: List[String], title: Option[String] = None) = {
      new NodeProperties((id: String) => vis.GraphNode.coloredNode(id, label, colors, title))
    }
  }

  class DrawGraph(container: dom.Node, options: js.Object = js.Dynamic.literal().asInstanceOf[js.Object]) {
    var network: Option[vis.Network] = None
    var onNodeSelected: js.Any => Unit = (_: js.Any) => {}

    def redraw(primitives: Seq[PrimitiveGroup], nodeProperties: NodeDescription => NodeProperties, onSelected: NodeDescription => Unit): Unit = {
      val graph = Debugger.calculateGraph(primitives)

      val nodes = graph.nodes.map { x =>
        nodeProperties(x.node).graphNode(x.id)
      }

      val edges = graph.edges.map { x => new vis.GraphEdge(x.from, x.to) }

      val nodeHashMap: HashMap[String, NodeDescription] = HashMap(graph.nodes.collect { case GraphNode(id, p) => (id, p) }: _*)

      val result = network match {
        case None =>
          val result = new vis.Network(container, vis.NetworkData(nodes, edges), options)
          result.on("selectNode", (x: js.Any) => onNodeSelected(x))
          network = Some(result)
          result
        case Some(result) =>
          result.setData(vis.NetworkData(nodes, edges))
          result
      }

      onNodeSelected = (obj: js.Any) => {
        nodeHashMap.get(obj.asInstanceOf[js.Dynamic].nodes.asInstanceOf[js.Array[String]](0)).map {
          x => onSelected(x)
        }
      }
    }
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
