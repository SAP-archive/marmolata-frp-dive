package com.sap.marmolata.react.react.impls.selfrx.debugger.visualization

import com.sap.marmolata.react.api.ReactiveLibrary
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.jquery.jQuery
import com.sap.marmolata.react.react.impls.selfrx.{Primitive, Signal}
import com.sap.marmolata.react.react.impls.selfrx.debugger.facades.TreeView._
import com.sap.marmolata.react.react.impls.selfrx.debugger.updatelogger.{OurRecordingSliceBuilder, RecordingLogUpdates}
import com.sap.marmolata.react.react.impls.selfrx.debugger.visualization.SimpleDomWrapper._

import scalajs.js.JSConverters._
import scala.scalajs.js

trait ShowTree {
  protected def colorChooser: ColorChooser
  protected def redrawGraph: () => Unit
  protected def tree: js.Array[TreeNode]

  val treeView: dom.Element

  def createTreeView(): dom.Element = {
    val result = newNode("div", Css("width", "100%"))
    jQuery(result).treeview(
      data = tree,
      levels = 1,
      multiSelect = true,
      showTags = true,
      //TODO: show checkboxes only on relevant nodes,
      //      unfortunately, this is currently not supported by bootstrap-treeview
      //      see also https://github.com/jonmiles/bootstrap-treeview/issues/187
      showCheckbox = true,
      selectedBackColor = "white",
      selectedColor = "black",
      onNodeUnselected = (event: js.Any, data: TreeNodeRef) => {
        val node = jQuery(result).treeview(getNode, data.nodeId)
        node.color = colorChooser.nextColor()
        redrawGraph()
      },
      onNodeSelected = (event: js.Any, data: TreeNodeRef) => {
        colorChooser.giveBackColor(data.color)
        redrawGraph()
      },
      onNodeChecked = (events: js.Any, data: TreeNodeRef) => {
        redrawGraph()
      },
      onNodeUnchecked = (events: js.Any, data: TreeNodeRef) => {
          redrawGraph()
      })

    result
  }

  def currentlySelected: Seq[GraphNode] = {
    val result = jQuery(treeView).treeview(getUnselected)
    result.toSeq.map(t => GraphNode(GraphNodeKey(t.color, t.state.checked), t.primitives))
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
      val now = prim match {
        case signal: Signal[_] =>
          s"=== ${signal.now} ==="
        case _ =>
          ""
      }
      new TreeNode(
        text = s"[${prim.getClass.getSimpleName}] $now",
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
        text = fp.map(_.toString).getOrElse("<direct>"),
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
        }: _*))
    }: _*)

  val treeView = createTreeView()
}

class ShowHistory(updates: RecordingLogUpdates, sourceMapConsumerForFile: SourceMapConsumerForFile,
                  val colorChooser: ColorChooser, val redrawGraph: () => Unit) extends ShowTree {
  //TODO: i18n
  private val now = new js.Date()

  private def timeFromNow(date: js.Date): String = {
    val q = (now.getTime() - date.getTime()).toInt / 1000
    if (q < 100) {
      s"${q} seconds ago"
    }
    else if (q < 1000) {
      s"${q / 60} minutes ago"
    }
    else {
      date.toLocaleTimeString()
    }
  }

  override protected def tree: js.Array[TreeNode] = {
    def treeNode(slice: OurRecordingSliceBuilder): TreeNode = {
      val stackTrace = slice.stackTrace.map(sourceMapConsumerForFile.originalPositionFor).map(p =>
        new TreeNode(text = p.toString, tags = js.Array(), primitives = Seq.empty)
      )
      val stackNode = new TreeNode(
        text = "stack",
        tags = js.Array(),
        primitives = Seq.empty,
        nodes = stackTrace.toJSArray
      )
      new TreeNode(
        text = s"${timeFromNow(new js.Date(slice.time.getTime().toDouble))}",
        tags = js.Array(slice.updatedValues.length.toString),
        primitives = slice.updatedValues,
        nodes = stackNode +: slice.children.map(treeNode).toJSArray
      )
    }
    updates.recordingHistory.reverse.map(treeNode).toJSArray
  }

  override val treeView: Element = createTreeView()
}