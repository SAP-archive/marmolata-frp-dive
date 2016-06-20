package react.selfrx.debugger.facades.vis

import scala.scalajs.js
import scala.scalajs.js.annotation.{ScalaJSDefined, JSExportAll, JSName, JSExport}
import js.JSConverters._

@ScalaJSDefined
class GraphNode(val id: String, val label: String) extends js.Object

@ScalaJSDefined
class GraphEdge(val from: String, val to: String) extends js.Object

@ScalaJSDefined
trait NetworkData extends js.Object

@js.native
@JSName("vis.DataSet")
class DataSet(v: js.Array[_]) extends js.Any

object NetworkData {
  def apply(nodes: Seq[GraphNode], edges: Seq[GraphEdge]): NetworkData = {
    js.Dynamic.literal(nodes = new DataSet(nodes.toJSArray), edges = new DataSet(edges.toJSArray)).asInstanceOf[NetworkData]
  }
}

@js.native
@JSName("vis.Network")
class Network(container: org.scalajs.dom.Node, data: NetworkData, options: js.Object = new js.Object()) extends js.Object {
}
