package react.selfrx

package debugger

import reactive.selfrx.{Signal, Event, SelfRxLogging, Primitive}

import scala.collection.mutable

class Debugger extends SelfRxLogging {
  private var elements: mutable.HashSet[Primitive] = mutable.HashSet.empty

  override def createPrimitive(p: Primitive): Unit = {
    elements += p
  }

  def drawFile(filename: String): Unit = {
    import java.io._
    val pw = new PrintWriter(new File(filename))
    drawCurrent(pw.print)
    pw.close()
  }

  def logInConsle(): Unit = {
    var s : String = ""
    drawCurrent { w => s += w }
    print(s)
  }

  def drawCurrent(into: String => Unit): Unit = {
    into(s"digraph selfrximage {\n")

    val allEle: mutable.HashMap[Primitive, Int] = mutable.HashMap.empty
    var toInsert = elements.toList
    elements foreach { x =>
      allEle += ((x, allEle.size))
    }

    while(!toInsert.isEmpty) {
      val next = toInsert.head
      toInsert = toInsert.tail

      next.getChildren() foreach { x =>
        if (!allEle.contains(x)) {
          allEle += ((x, allEle.size))
          toInsert = x +: toInsert
        }
      }
    }

    val result

    allEle.foreach { case (p, n) =>
      // see https://issues.scala-lang.org/browse/SI-6476
      val label = p match {
        case z: Event[_] =>
          "color=red,label=\"" + z.toString + "\""
        case z: Signal[_] =>
          "color=green,label=\"" + z.toString + "\\n" + s"${z.now.toString}: ${z.now.getClass.toString}" + "\""
        case z =>
          "label=\"" + z.toString + "\""
      }

      into(s"D${n} [$label]\n")
    }

    allEle.foreach { case (p, n) =>
      p.getChildren().foreach { c =>
        val nc = allEle(c)
        into(s"D${n} -> D${nc}\n")
      }
    }

    into("}\n")
  }
}