package com.sap.marmolata.react.impls.selfrx.debugger.visualization

import com.sap.marmolata.react.api.ReactiveLibrary
import com.sap.marmolata.react.react.impls.selfrx.Primitive
import com.sap.marmolata.react.react.impls.selfrx.debugger.JavaScriptFunctions.NodeProperties

trait GraphNodeVisualizationAnnotation extends Annotation {
  def graphNode(p: Primitive, colors: Seq[String]): NodeProperties
}

private object PrivObj

case class NamedGraphNodeAnnotation(name: String, title: Option[String] = None, color: String = "green") extends GraphNodeVisualizationAnnotation {
  override def graphNode(p: Primitive, colors: Seq[String]): NodeProperties = NodeProperties(name, color, title)
  override def description: String = "NamedGraphNode"

  override def hashCode(): Int = PrivObj.hashCode()
  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[NamedGraphNodeAnnotation]
}