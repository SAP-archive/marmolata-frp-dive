import scala.scalajs.js.annotation.{JSExportAll, JSName, JSExport}

import reactive.library._
import reactive.library.syntax._
import org.scalajs.jquery._

@JSExport("ReactiveMachinary")
@JSExportAll
object Main {
  def main(): Unit = {
    val v = Var(0)

    jQuery { () =>
      jQuery().mainDiv.html(
        s"""
           | <table id="last-steps">
           |
           | </table>
         """.stripMargin)
    }
  }

}
