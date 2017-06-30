package com.sap.marmolata.react.react.impls.selfrx.debugger.visualization

import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.raw.Element
import org.scalajs.jquery._
import com.sap.marmolata.react.react.debug.{AnnotateStack, InternalStrictMap}
import com.sap.marmolata.react.react.impls.selfrx
import com.sap.marmolata.react.react.impls.selfrx.debugger.Debugger
import com.sap.marmolata.react.react.impls.selfrx.{Observable, Primitive}
import Debugger._
import com.sap.marmolata.react.react.impls.selfrx.debugger.JavaScriptFunctions.NodeProperties
import com.sap.marmolata.react.react.impls.selfrx.debugger.updatelogger.RecordingLogUpdates
import com.sap.marmolata.react.react.impls.selfrx.debugger.JavaScriptFunctions

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportAll, JSName, ScalaJSDefined}
import SimpleDomWrapper._
import com.sap.marmolata.react.api.ReactiveLibrary

case class GraphNodeKey(color: String, singleNodes: Boolean = false)

object GraphNodeKey {
  implicit object GraphNodeKeyOrdering extends Ordering[GraphNodeKey] {
    override def compare(x: GraphNodeKey, y: GraphNodeKey): Int = {
      val result = x.color compare y.color
      if (result == 0) {
        x.singleNodes compare y.singleNodes
      }
      else {
        result
      }
    }
  }
}

case class GraphNode(key: GraphNodeKey, primitives: Seq[Primitive])

class DrawGraph(container: dom.Node) {
  val drawGraph: JavaScriptFunctions.DrawGraph = new JavaScriptFunctions.DrawGraph(container,
    js.Dynamic.literal(
      edges = js.Dynamic.literal(arrows = "to")))

  def redraw(selected: Seq[GraphNode])(onSelected: NodeDescription => Unit): Unit = {
    val allPrimitives: Map[Seq[GraphNodeKey], Seq[Primitive]] =
      selected.toSeq.flatMap (d => d.primitives.map(p => (p, d.key))).
      groupBy(_._1).mapValues { _.map(_._2).sorted.distinct }.toSeq.groupBy(_._2).mapValues(_.map(_._1))

    val primGroups = allPrimitives.toSeq.flatMap {
      case (colors, prims) =>
        if (colors.exists(_.singleNodes)) {
          //TODO: do some SinglePrimitive
          prims.map(p => Debugger.PrimitiveGroup(colors, Seq(p)))
        }
        else {
          Seq(Debugger.PrimitiveGroup(colors, prims))
        }
    }

    def isObservable(p: Primitive): Boolean = {
      p.isInstanceOf[Observable] && !p.containsTag(InternalStrictMap)
    }

    def isNormal(p: Primitive): Boolean = {
      !p.containsTag(InternalAnnotation)
    }

    drawGraph.redraw(primGroups, {
      case MultipleImportantNodes(PrimitiveGroup(_colors, prims)) =>
        //TODO: add SingleNode
        if (prims.length != 1) {
          val colors = _colors.asInstanceOf[Seq[GraphNodeKey]]
          val obsCount = prims.count(isObservable)
          val anyCount = prims.count(isNormal)
          NodeProperties.coloredNode(s"${anyCount} ($obsCount)", colors.map(_.color).toList)
        }
        else {
          val prim = prims.head
          prim.allAnnotations.collectFirst { case x: GraphNodeVisualizationAnnotation => x } match {
            case None =>
              val colors = _colors.asInstanceOf[Seq[GraphNodeKey]]
              val title = prim match {
                case x: selfrx.Signal[_] =>
                  Some(x.now.toString)
                case _ =>
                  None
              }
              NodeProperties.coloredNode(prim.getClass.getSimpleName, colors.map(_.color).toList, title)
            case Some(x) =>
              x.graphNode(prim, _colors.asInstanceOf[Seq[GraphNodeKey]].map(_.color))
          }

        }
      case MultipleNodes(_, prims) =>
        val obsCount = prims.count(isObservable)
        val anyCount = prims.count(isNormal)
        NodeProperties(s"${anyCount} (${obsCount})", "yellow", None)
    }, onSelected)
  }
}

case class TreeDefinition(name: String, prims: Seq[Primitive], showFiles: ShowFiles, showTags: ShowTags) {
  def currentlySelected: Seq[com.sap.marmolata.react.react.impls.selfrx.debugger.visualization.GraphNode] = {
    showFiles.currentlySelected ++ showTags.currentlySelected
  }
}

object TreeDefinition {
  def newTreeDefinition(name: String, prims: Seq[Primitive], stackTrace: AnnotateStack, colorChooser: ColorChooser,
                        sourceMapConsumerForFile: SourceMapConsumerForFile, redrawGraph: () => Unit) = {
    TreeDefinition(name, prims,
      new ShowFiles(prims.map(x => (x, stackTrace.stackFrom(x))).toMap, colorChooser, sourceMapConsumerForFile, redrawGraph),
      new ShowTags(prims.toSet, colorChooser, redrawGraph))
  }
}

class TreeViewWithGraphView(allPrims: Seq[Primitive], stackTrace: AnnotateStack,
                            sourceMapConsumerForFile: SourceMapConsumerForFile, recordingLogUpdates: RecordingLogUpdates) {
  private val colorChooser = new ColorChooser()

  private var trees: Seq[TreeDefinition] =
    Seq(TreeDefinition.newTreeDefinition(s"Main (${allPrims.size})", allPrims, stackTrace, colorChooser, sourceMapConsumerForFile, redrawGraph))

  private val toolbar = newNode("ul", CssClass("nav"), CssClass("nav-tabs"))
  private var currentlyActive: Int = 0

  private val historyTreeViewNode = newNode("div", Css("width", "100%"))
  private val treeView2 = newNode("div", Css("width", "100%"))
  val treeView = newNode("div", Css("width", "100%"), historyTreeViewNode, toolbar, treeView2)
  val graphView = newNode("div", Css("width", "100%"), Css("height", "100%"))
  val drawGraph: DrawGraph = new DrawGraph(graphView)
  val historyTreeView: ShowTree = new ShowHistory(recordingLogUpdates, sourceMapConsumerForFile, colorChooser, redrawGraph)
  historyTreeViewNode.appendChild(historyTreeView.treeView)

  private def redrawToolbar(): Unit = {
    jQuery(toolbar).empty()
    trees.zipWithIndex foreach { case (tree, index) =>
      val nn = newNode("li", if (index == currentlyActive) CssClass("active") else NoAttribute,
        newNode("a", tree.name))
      jQuery(toolbar).append(nn)
      jQuery(nn).click((_: JQueryEventObject) => newActiveTreeview(index))
    }
  }

  private def newActiveTreeview(index: Int): Unit = {
    jQuery(treeView2).children.detach
    jQuery(treeView2).append(trees(index).showTags.treeView)
    jQuery(treeView2).append(trees(index).showFiles.treeView)
    currentlyActive = index
    redrawToolbar()
  }

  private def redrawGraph(): Unit = {
    val selected = trees.flatMap(_.currentlySelected) ++ historyTreeView.currentlySelected
    drawGraph.redraw(selected) {
      case MultipleImportantNodes(PrimitiveGroup(_, prims)) =>
        addTree(TreeDefinition.newTreeDefinition(s"(${prims.length})", prims, stackTrace, colorChooser, sourceMapConsumerForFile, redrawGraph))
      case MultipleNodes(_, prims) =>
        addTree(TreeDefinition.newTreeDefinition(s"(${prims.length})", prims, stackTrace, colorChooser, sourceMapConsumerForFile, redrawGraph))
    }
  }

  def addTree(td: TreeDefinition): Unit = {
    trees = trees :+ td
    redrawToolbar()
  }

  newActiveTreeview(0)
}


case class FileElements(all: Seq[Primitive], lines: Map[FilePosition, Seq[Primitive]])


class ReactiveDebugger(debugger: Debugger, stackTrace: AnnotateStack,
                       sourceMapConsumerForFile: SourceMapConsumerForFile, recordingLogUpdates: RecordingLogUpdates)  {
  def height: Int = 300

  var inner: dom.Element = null

  def renderAt(domElement: Element): Unit = {
    val refreshDom = newNode("button", "reload")
    val buttonsBar = newNode("div", refreshDom)

    val treeView = newNode("div", Css("display", "inline-block"), Css("margin", "0"), Css("width", "50%"), Css("height", "100%"), Css("overflow-y", "scroll"), buttonsBar)
    val graphView = newNode("div", Css("display", "inline-block"), Css("margin", "0"), Css("width", "50%"), Css("height", "100%"))

    val inner = newNode("div", treeView, graphView, Css("height", height.toString), Css("width", "100%"))
    this.inner = inner

    domElement.appendChild(inner)

    jQuery(refreshDom).click { (_: JQueryEventObject) =>
      1 until treeView.children.length map { treeView.children(_) } foreach {
        treeView.removeChild(_)
      }

      0 until graphView.children.length map { graphView.children(_) } foreach {
        graphView.removeChild(_)
      }

      val tv = new TreeViewWithGraphView(debugger.allElelements, stackTrace, sourceMapConsumerForFile, recordingLogUpdates)
      treeView.appendChild(tv.treeView)
      graphView.appendChild(tv.graphView)
    }
  }
}
