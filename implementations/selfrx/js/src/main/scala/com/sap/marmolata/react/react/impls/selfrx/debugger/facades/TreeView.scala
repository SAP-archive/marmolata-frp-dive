package com.sap.marmolata.react.react.impls.selfrx.debugger.facades

import org.scalajs.jquery._
import com.sap.marmolata.react.react.impls.selfrx.Primitive

import scala.scalajs.js
import scala.scalajs.js.Dynamic
import scala.scalajs.js.annotation.ScalaJSDefined

object TreeView {

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

  @js.native
  trait NodeId extends js.Any

  @js.native
  trait TreeNodeRef extends js.Any {
    def nodeId: NodeId = js.native
    def color: String = js.native
  }

  object getNode
  object getUnselected

  implicit class JQueryTreeview(element: JQuery) {
    def treeview(data: js.Array[TreeNode], levels: Int, multiSelect: Boolean, showTags: Boolean, showCheckbox: Boolean,
                 selectedBackColor: String, selectedColor: String,
                 onNodeUnselected: (js.Any, TreeNodeRef) => Unit, onNodeSelected: (js.Any, TreeNodeRef) => Unit,
                 onNodeChecked: (js.Any, TreeNodeRef) => Unit, onNodeUnchecked: (js.Any, TreeNodeRef) => Unit): Unit = {
      element.asInstanceOf[js.Dynamic].treeview(
        Dynamic.literal(
          data = data,
          levels = levels,
          multiSelect = multiSelect,
          showTags = showTags,
          showCheckbox = showCheckbox,
          selectedBackColor = selectedBackColor,
          selectedColor = selectedColor,
          onNodeUnselected = onNodeUnselected,
          onNodeSelected = onNodeSelected,
          onNodeChecked = onNodeChecked,
          onNodeUnchecked = onNodeUnchecked))
    }

    def treeview(f: getNode.type, nodeId: NodeId): TreeNode = {
      element.asInstanceOf[js.Dynamic].treeview("getNode", nodeId).asInstanceOf[TreeNode]
    }

    def treeview(f: getUnselected.type): js.Array[TreeNode] = {
      element.asInstanceOf[js.Dynamic].treeview("getUnselected").asInstanceOf[js.Array[TreeNode]]
    }
  }
}