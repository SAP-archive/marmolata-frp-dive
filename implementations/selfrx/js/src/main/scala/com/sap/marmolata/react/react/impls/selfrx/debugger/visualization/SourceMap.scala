package com.sap.marmolata.react.react.impls.selfrx.debugger.visualization

import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.annotation.ScalaJSDefined
import scala.scalajs.js
import org.scalajs.jquery.jQuery

@js.native
trait OriginalPosition extends js.Object {
  val source: String = js.native
  val column: Int = js.native
  val line: Int = js.native
}

@ScalaJSDefined
class JavascriptPosition(
  val column: Int,
  val line: Int
  /*, val bias: js.Any = sourceMapConsumer.asInstanceOf[js.Dynamic].LEAST_UPPER_BOUND*/)
  extends js.Object

object JavascriptPosition {
  def apply(s: StackTraceElement): JavascriptPosition =
    new JavascriptPosition(s.asInstanceOf[js.Dynamic].getColumnNumber().asInstanceOf[Int], s.getLineNumber)
}

case class Filename(source: String)

@js.native
@JSName("sourceMap.SourceMapConsumer")
class SourceMapConsumer(data: js.Any) extends js.Any {
  def originalPositionFor(obj: JavascriptPosition): OriginalPosition = js.native
}

class SourceMapConsumerForFile(sourceMapPath: String) {
  //"/js/gllineitems-fastopt.js.map"
  private var sourceMapConsumer: SourceMapConsumer = null
  jQuery.get(sourceMapPath, {
    (data: js.Any) =>
      sourceMapConsumer = new SourceMapConsumer(data)
  })

  def filename(x: StackTraceElement): Filename = {
    Filename(sourceMapConsumer.originalPositionFor(JavascriptPosition(x)).source)
  }

  def originalPositionFor(x: StackTraceElement): FilePosition =
    FilePosition.fromOriginalPosition(sourceMapConsumer.originalPositionFor(JavascriptPosition(x)), JavascriptPosition(x))
}

case class FilePosition(file: String, line: Int, column: Int, origLine: Int, origColumn: Int) {
  def isSameAs(x: JavascriptPosition): Boolean = {
    origLine == x.line && origColumn == x.column
  }

  override def toString(): String = s"${file}:${line}:${column} (${origLine}:${origColumn})"

}

object FilePosition {
  def fromOriginalPosition(y: OriginalPosition, orig: JavascriptPosition): FilePosition = {
    FilePosition(file = y.source, line = y.line, column = y.column, origLine = orig.line, origColumn = orig.column)
  }
}
