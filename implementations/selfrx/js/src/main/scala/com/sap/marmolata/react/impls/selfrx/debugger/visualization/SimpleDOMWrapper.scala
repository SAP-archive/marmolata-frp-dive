package com.sap.marmolata.react.impls.selfrx.debugger.visualization

import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.jquery.jQuery

import scala.scalajs.js

import language.implicitConversions

object SimpleDomWrapper {
  trait DomProperty {
    def addToDomNode(node: dom.Node)
  }

  @js.native
  trait ValueNode extends dom.Element {
    def value: String
  }

  object DomProperty {
    implicit def domNodeToDomProperty(v: dom.Node): DomProperty =
      new DomProperty {
        override def addToDomNode(node: Node): Unit = node.appendChild(v)
      }

    implicit def textToDomProperty(s: String): DomProperty =
      domNodeToDomProperty(dom.document.createTextNode(s))
  }

  case class Css(val key: String, val value: String) extends DomProperty {
    override def addToDomNode(node: dom.Node): Unit = {
      jQuery(node).css(key, value)
    }
  }

  case class CssClass(val name: String) extends DomProperty {
    override def addToDomNode(node: Node): Unit =
      jQuery(node).addClass(name)
  }

  object NoAttribute extends DomProperty {
    override def addToDomNode(node: Node): Unit = {}
  }

  def newNode(name: String, props: DomProperty*): dom.Element = {
    val result = dom.document.createElement(name)
    props.foreach { _.addToDomNode(result) }
    result
  }

  def newValueNode(name: String, props: DomProperty*): ValueNode = {
    newNode(name, props: _*).asInstanceOf[ValueNode]
  }
}
