package com.sap.marmolata.react.react.impls.selfrx.debugger.visualization

import scala.util.Random

// generate colors that are well distingushible, see http://martin.ankerl.com/2009/12/09/how-to-create-random-colors-programmatically/
class ColorChooser {
  // HSV values in [0..1[
  // returns [r, g, b] values from 0 to 255
  private def hsv_to_rgb(h: Double, s: Double, v: Double): String = {
    val h_i = (h * 6).toInt
    val f = h * 6 - h_i
    val p = v * (1 - s)
    val q = v * (1 - f * s)
    val t = v * (1 - (1 - f) * s)
    val (r, g, b) = h_i match {
      case 0 =>
        (v, t, p)
      case 1 =>
        (q, v, p)
      case 2 =>
        (p, v, t)
      case 3 =>
        (p, q, v)
      case 4 =>
        (t, p, v)
      case 5 =>
        (v, p, q)
    }
    val result = Integer.toHexString((r * 256).toInt * 256 * 256 + (g * 256).toInt * 256 + (b * 256).toInt)
    var spaces = "#"
    1 to (6 - result.length) foreach { _ => spaces += "0" }
    spaces + result
  }

  //use golden ratio
  private def golden_ratio_conjugate = 0.618033988749895
  private var h = Random.nextDouble()

  private def genNext(): String = {
    h += golden_ratio_conjugate
    h %= 1
    hsv_to_rgb(h, 0.5, 0.95)
  }

  private var currentlyAvailableColors: List[String] = List.empty

  def nextColor(): String = {
    currentlyAvailableColors match {
      case head :: tail =>
        currentlyAvailableColors = tail
        head
      case Nil =>
        genNext()
    }
  }

  def giveBackColor(color: String): Unit = {
    currentlyAvailableColors = color :: currentlyAvailableColors
  }
}
