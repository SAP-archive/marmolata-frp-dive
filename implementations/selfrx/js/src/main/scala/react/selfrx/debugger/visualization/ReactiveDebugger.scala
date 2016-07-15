package react.selfrx.debugger.visualization

import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.raw.Element
import org.scalajs.jquery._
import react.ReactiveLibrary.{InternalAnnotation, Annotation}
import react.debug.{InternalStrictMap, AnnotateStack}
import react.selfrx.debugger.Debugger._
import react.selfrx.debugger.JavaScriptFunctions.NodeProperties
import react.selfrx.debugger.{JavaScriptFunctions, Debugger}
import reactive.selfrx.{Observable, Signal, Primitive}

import scala.scalajs.js
import scala.scalajs.js.Dynamic
import scala.scalajs.js.annotation.{JSExportAll, ScalaJSDefined, JSName}

import scala.util.Random

import SimpleDomWrapper._

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
                case x: Signal[_] =>
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
  def currentlySelected: Seq[react.selfrx.debugger.visualization.GraphNode] = {
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

class TreeViewWithGraphView(allPrims: Seq[Primitive], stackTrace: AnnotateStack, sourceMapConsumerForFile: SourceMapConsumerForFile) {
  private val colorChooser = new ColorChooser()

  private var trees: Seq[TreeDefinition] =
    Seq(TreeDefinition.newTreeDefinition(s"Main (${allPrims.size})", allPrims, stackTrace, colorChooser, sourceMapConsumerForFile, redrawGraph))

  private val toolbar = newNode("ul", CssClass("nav"), CssClass("nav-tabs"))
  private var currentlyActive: Int = 0

  private val treeView2 = newNode("div", Css("width", "100%"))
  val treeView = newNode("div", Css("width", "100%"), toolbar, treeView2)
  val graphView = newNode("div", Css("width", "100%"), Css("height", "100%"))
  val drawGraph: DrawGraph = new DrawGraph(graphView)

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
    val selected = js.Array(trees.flatMap(_.currentlySelected): _*)
    drawGraph.redraw(selected.toSeq) {
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

@ScalaJSDefined
class TreeNodeState(
  var checked: Boolean = false,
  var disabled: Boolean = false,
  var expanded: Boolean = false,
  var selected: Boolean = true) extends js.Object

@ScalaJSDefined
class TreeNode(
  val text: String,
  val tags: js.Array[String],
  val nodes: js.UndefOr[js.Array[TreeNode]] = js.undefined,
  val primitives: Seq[Primitive],
  val state: TreeNodeState = new TreeNodeState(),
  var color: String = "black",
  val showCheckbox: Boolean = true
) extends js.Object

trait ShowTree {
  protected def colorChooser: ColorChooser
  protected def redrawGraph: () => Unit
  protected def tree: js.Array[TreeNode]

  val treeView: dom.Element

  def createTreeView(): dom.Element = {
    val result = newNode("div", Css("width", "100%"))

    jQuery(result).asInstanceOf[js.Dynamic].treeview(
      Dynamic.literal(
        data = tree,
        levels = 1,
        multiSelect = true,
        showTags = true,
        showCheckbox = true,
        selectedBackColor = "white",
        selectedColor = "black",
        onNodeUnselected = (event: js.Any, data: js.Dynamic) => {
          val node = jQuery(result).asInstanceOf[js.Dynamic].treeview("getNode", data.nodeId).asInstanceOf[TreeNode]
          node.color = colorChooser.nextColor()
          redrawGraph()
        },
        onNodeSelected = (event: js.Any, data: TreeNode) => {
          colorChooser.giveBackColor(data.color)
          redrawGraph()
        },
        onNodeChecked = (events: js.Any, data: TreeNode) => {
          redrawGraph()
        },
        onNodeUnchecked = (events: js.Any, data: TreeNode) => {
          redrawGraph()
        }))

    result
  }



  def currentlySelected: Seq[react.selfrx.debugger.visualization.GraphNode] = {
    val result = jQuery(treeView).asInstanceOf[js.Dynamic].treeview("getUnselected").asInstanceOf[js.Array[TreeNode]]
    result.toSeq.map(t => react.selfrx.debugger.visualization.GraphNode(GraphNodeKey(t.color, t.state.checked), t.primitives))
  }
}

case class TagTree(val currentTag: Option[Annotation], var children: Map[Annotation, TagTree]) {
  def addTag(tag: Annotation): Option[TagTree] = {
    val parent: Option[Annotation] = tag.parent
    if (parent == currentTag) {
      Some(children.getOrElse(tag, {
        val result = TagTree(Some(tag), Map.empty)
        children += ((tag, result))
        result
      }))
    }
    else {
      parent match {
        case Some(t) =>
          val n = addTag(t)
          n.flatMap(_.addTag(tag))
        case None =>
          None
      }
    }
  }

  def toTreeNode(allPrims: Seq[Primitive]): js.UndefOr[js.Array[TreeNode]] = {
    if (children.isEmpty) {
      js.undefined
    }
    else {
      js.Array(
        children.map { case (tag, tagTree) =>
          val prims = allPrims.filter(_.containsTag(tag))
          new TreeNode(
            text = tag.description,
            tags = js.Array(s" ${prims.size} "),
            primitives = prims,
            nodes = tagTree.toTreeNode(prims)
          )
        }.toSeq: _*)
    }
  }
}

object TagTree {
  def apply(tags: Seq[Annotation]): TagTree = {
    val result = TagTree(None, Map.empty)
    tags.foreach { result.addTag(_) }
    result
  }
}

class ShowTags(
  reactives: Set[Primitive],
  val colorChooser: ColorChooser,
  val redrawGraph: () => Unit
) extends ShowTree {

  private val tags: Set[Annotation] = reactives.flatMap(_.allAnnotations)

  private val tagTree: TagTree = TagTree(tags.toSeq)

  val tree = tagTree.toTreeNode(reactives.toSeq).getOrElse(js.Array())

  val treeView = createTreeView()
}

class ShowFiles(
   reactives: Map[Primitive, Seq[StackTraceElement]],
   val colorChooser: ColorChooser,
   sourceMapConsumerForFile: SourceMapConsumerForFile,
   val redrawGraph: () => Unit) extends ShowTree {
  val files: Map[Filename, FileElements] = {
    val listOfFiles =
      reactives.toSeq.flatMap {
        case (prim, eles) =>
          eles.map { x =>
            (sourceMapConsumerForFile.filename(x), prim)
          }
      }.groupBy(_._1)

    listOfFiles.map { case (file, prims) =>
      val filteredReactives: Seq[(Primitive, FilePosition)] = prims.map(_._2).distinct.map { (ref: Primitive) =>
        val stack = reactives(ref)
        for {
          filePosition <- stack.collectFirst(Function.unlift { x =>
            val original = sourceMapConsumerForFile.originalPositionFor(x)
            if (original.file == file.source)
              Some(original)
            else
              None
          })
        } yield ((ref, filePosition))
      }.collect { case Some(x) => x }.toSeq

      val lines = filteredReactives.groupBy(_._2).mapValues(_.map(_._1))

      (file, FileElements(filteredReactives.map(_._1), lines))
    }
  }

  def directNodes(prims: Seq[Primitive]): js.Array[TreeNode] = {
    js.Array(prims.map(x => {
      val prim = x
      val now =
        if (prim.isInstanceOf[Signal[_]]) {
          s"=== ${prim.asInstanceOf[Signal[_]].now} ==="
        }
        else {
          ""
        }
      new TreeNode(
        text = s"[${prim.getClass}] $now",
        tags = js.Array(),
        showCheckbox = false,
        primitives = Seq(prim)
      )
    }): _*)
  }

  def nodesFor(filePosition: FilePosition, prims: Seq[(Option[Int], Primitive)]): js.Array[TreeNode] = {
    val directlyCalling: Seq[((Primitive, Int), Option[FilePosition])] =
      prims.map { case (_index, pr) =>
        val stack = reactives(pr)
        val index = _index.getOrElse(stack.zipWithIndex.find { case (st, _) =>
          filePosition.isSameAs(JavascriptPosition(st))
        }.get._2)
        if (index > 1) {
          val ele = stack(index - 1)
          ((pr, index - 1), Some(sourceMapConsumerForFile.originalPositionFor(ele)))
        }
        else
          ((pr, 0), None)
      }

    val result = directlyCalling.groupBy(_._2).toSeq.sortBy{ case (_, sq) => sq.length }.map { case (fp, sq) =>
      new TreeNode(
        text = fp.map(x => s"${x.file}:${x.line}:${x.column} (${x.origLine}:${x.origColumn})").getOrElse("<direct>"),
        tags = js.Array(sq.length.toString),
        nodes = js.UndefOr.any2undefOrA(fp.map(x => nodesFor(x, sq.map(_._1 match { case (x, y) => (Some(y), x) }))).getOrElse(directNodes(sq.map(_._1._1)))),
        primitives = sq.map(_._1._1)
      )
    }
    js.Array(result: _*)
  }

  val tree =
      js.Array(files.toSeq.sortBy { case (_, FileElements(allPrimitives, _)) => allPrimitives.length }.map { case (filename, FileElements(allPrimitives, lines)) =>
        new TreeNode(
          text = filename.source,
          tags = js.Array(s" ${allPrimitives.length} "),
          primitives = allPrimitives,
          nodes = js.Array(lines.toSeq.sortBy { case (_, allPrims) => allPrims.length }.map { case (filePosition, allPrims) =>
            new TreeNode(
              text = s"${filePosition.line}:${filePosition.column} (orig: ${filePosition.origLine}:${filePosition.origColumn})",
              tags = js.Array(s" ${allPrims.length} "),
              primitives = allPrims,
              nodes = nodesFor(filePosition, allPrims.map((None, _)))
            )
          }.toSeq: _*))
      }.toSeq: _*)

  val treeView = createTreeView()
}

class ReactiveDebugger(debugger: react.selfrx.debugger.Debugger, stackTrace: AnnotateStack, sourceMapConsumerForFile: SourceMapConsumerForFile)  {
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

      val tv = new TreeViewWithGraphView(debugger.allElelements, stackTrace, sourceMapConsumerForFile)
      treeView.appendChild(tv.treeView)
      graphView.appendChild(tv.graphView)
    }
  }
}
