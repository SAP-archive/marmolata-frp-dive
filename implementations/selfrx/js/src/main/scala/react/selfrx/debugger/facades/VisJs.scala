package react.selfrx.debugger.facades.vis

import org.scalajs.dom
import org.scalajs.dom.raw.{BlobPropertyBag, Blob}

import scala.scalajs.js
import scala.scalajs.js.annotation.{ScalaJSDefined, JSExportAll, JSName, JSExport}
import js.JSConverters._

@ScalaJSDefined
class GraphNode(
  val id: String,
  val label: String,
  val title: js.UndefOr[String] = js.undefined,
  val color: js.UndefOr[String] = js.undefined,
  val image: js.UndefOr[js.Any] = js.undefined,
  val shape: js.UndefOr[String] = js.undefined)
extends js.Object

object GraphNode {
  def coloredNode(id: String, label: String, colors: List[String]) = {
    val data =
      s"""
        |<svg xmlns="http://www.w3.org/2000/svg" width="50" height="${colors.length * 50}">
        |${colors.zipWithIndex.map { case (color, index) =>
              s"""
                |<rect x="0" y="${index * 50 / colors.length}" width="100%" height="${(index + 1) * 50 / colors.length - index * 50 / colors.length}" fill="${color}" stroke-width="5" stroke="#ffffff" ></rect>
              """.stripMargin
            }.mkString
          }
        |</svg>
      """.stripMargin
    val image = dom.window.asInstanceOf[js.Dynamic].URL.createObjectURL(
      new Blob(js.Array(data), BlobPropertyBag("image/svg+xml;charset=utf-8")))

    new GraphNode(id = id,
      label = label,
      title = js.undefined,
      image = image,
      shape = "image"
    )
  }
}



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
  def destroy(): Unit = js.native
  def on(eventName: String, callback: js.Function1[js.Any, Unit]): Unit = js.native
}
