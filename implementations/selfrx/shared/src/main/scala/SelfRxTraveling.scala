package react.selfrx

package debugger

import java.time.LocalTime

import react.ReactiveLibrary.Nameable
import react.debug.{HasUnderlying, DebugLayer}
import react.selfrx.debugger.Debugger.{OtherSignal, MappedSignal, GraphEdges, GraphNodes}
import reactive.selfrx._

import scala.collection.immutable.HashMap
import scala.collection.{mutable, SortedSet}

class OurRecordingSliceBuilder extends RecordingSliceBuilder {
  var primitiveRecords: Seq[PrimitiveRecord[_]] = Seq.empty

  override def addPrimitiveChange[A](p: RecordForPlayback[A], before: A, after: A): Unit = {
    primitiveRecords :+= PrimitiveRecord(p, before, after)
  }

  override def currentRecordingMode: RecordingMode = RecordingMode.Record
}

case class PrimitiveRecord[A](p: RecordForPlayback[A], before: A, after: A)

case class RecordSlice(time: LocalTime,
                       name: String,
                       primitiveRecords: Seq[PrimitiveRecord[_]])

case class RecordingList(
  val previous: List[RecordSlice],
  val after: List[RecordSlice]
) {

  def goBack(tu: TriggerUpdate): RecordingList = {
    assert(!previous.isEmpty)
    def doPlayback(p: PrimitiveRecord[_]): Unit = {
      p.p.playback(p.before.asInstanceOf, tu)
    }
    previous.head.primitiveRecords.foreach(doPlayback)
    RecordingList(previous.tail, previous.head +: after)
  }

  def goForward(tu: TriggerUpdate): RecordingList = {
    assert(!after.isEmpty)
    def doPlayback(p: PrimitiveRecord[_]): Unit = {
      p.p.playback(p.after.asInstanceOf, tu)
    }
    after.head.primitiveRecords.foreach(doPlayback)
    RecordingList(after.head +: previous, after.tail)
  }

  def allowRecording: Boolean = after.isEmpty
  def +(r: RecordSlice): RecordingList = {
    assert(after.isEmpty)
    RecordingList(r +: previous, Nil)
  }
}

class OurRecording extends Recording {
  var records: RecordingList = new RecordingList(Nil, Nil)

  override def startNewRecording(): RecordingSliceBuilder =
    if (records.allowRecording) {
      new OurRecordingSliceBuilder()
    }
    else {
      DontRecordRecordingSliceBuilder
    }

  override def finishCurrentRecording(recording: RecordingSliceBuilder): Unit = {
    recording match {
      case o: OurRecordingSliceBuilder =>
        val label = s"update ${o.primitiveRecords.length} primitives"
        records += RecordSlice(LocalTime.now(), label, o.primitiveRecords)
      case _ =>
    }
  }

  def playBackwards(tu: TriggerUpdate): Unit = {
    records = records.goBack(tu)
  }

  def playForward(tu: TriggerUpdate): Unit = {
    records = records.goForward(tu)
  }
}

object DontRecordRecordingSliceBuilder extends RecordingSliceBuilder {
  override def addPrimitiveChange[A](p: RecordForPlayback[A], before: A, after: A): Unit = {}
  override def currentRecordingMode: RecordingMode = RecordingMode.Playback
}

class RecordedSelfRxImpl extends SelfRxImpl {
  override implicit lazy val recording: OurRecording = new OurRecording()

  def playBackward(continue: => Boolean): Unit = {
    TriggerUpdate.doPrimitiveUpdateUnconditionally(DontRecordRecordingSliceBuilder) { tu =>
      while(continue) {
        recording.playBackwards(tu)
      }
    }
  }

  def playForward(continue: => Boolean): Unit = {
    TriggerUpdate.doPrimitiveUpdateUnconditionally(DontRecordRecordingSliceBuilder) { tu =>
      while(continue) {
        recording.playForward(tu)
      }
    }
  }
}

trait DebuggerSelfRxImpl extends DebugLayer with DebugSelfRxImplJavaScriptInterface {
  val debugger: Debugger = new Debugger()

  override def onNew(u: HasUnderlying[Nameable]): Unit = {
    super.onNew(u)
    u.under match {
      case p: Primitive =>
        debugger.createPrimitive(p)
      case _ =>
    }
  }
}

object Debugger {
  sealed trait GraphNodeType
  sealed trait Signal extends GraphNodeType
  object Var extends Signal
  object MappedSignal extends Signal
  object OtherSignal extends Signal
  object ReassignableVar extends Signal

  sealed trait Event extends GraphNodeType
  object EventFromSignal extends Event
  object EventSource extends Event
  object OtherEvent extends Event

  object Observable extends GraphNodeType

  object Other extends GraphNodeType


  def colorOfNode(graphNodeType: GraphNodeType): String = {
    graphNodeType match {
      case _: Signal => "#FF0000"
      case _: Event => "#00FF00"
      case Observable => "#0000FF"
      case Other => "#000000"
    }
  }


  case class NodeAggregate(nrNodes: Int) extends GraphNodeType

  case class GraphNodes(id: String, label: String, detailed: String, typeOfNode: GraphNodeType)

  case class GraphEdges(from: String, to: String)

  case class GraphRepresentation(nodes: Seq[GraphNodes], edges: Seq[GraphEdges])
}

class Debugger extends JavaScriptInterface {
  private var elements: mutable.MutableList[Primitive] = mutable.MutableList.empty

  def createPrimitive(p: Primitive): Unit = {
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
    val g = currentGraph(Some.apply)

    into(s"digraph selfrximage {\n")
    g.nodes.foreach { x => into(s"${x.id} [label=" + "\"" + x.label + "\"]\n") }
    g.edges.foreach { x => into(s"${x.from} -> ${x.to}\n") }
    into(s"}\n")
  }

  def currentGraph(filterNodes: String => Option[String]): Debugger.GraphRepresentation = {
    val allEle: mutable.HashMap[Primitive, Int] = mutable.HashMap.empty
    val eleNames: mutable.HashMap[Primitive, String] = mutable.HashMap.empty
    var toInsert: List[Primitive] = List.empty
    elements foreach { x =>
      filterNodes(x.name).map { s =>
        allEle += ((x, allEle.size))
        eleNames += ((x, s))
        toInsert = x +: toInsert
      }
    }

    val countImportant = allEle.size

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

    //TODO: there has to be something better in the/some standard library
    final class Heap(private val value: Int) {
      private var parent: Option[Heap] = None

      def getParent: Heap = {
        parent match {
          case None => this
          case Some(p) => {
            val result = p.getParent
            parent = Some(result)
            result
          }
        }
      }

      def merge(other: Heap): Unit = {
        getParent.merge0(other.getParent)
      }

      private def merge0(other: Heap): Unit = {
        assert(parent.isEmpty && other.parent.isEmpty)

        if (this != other) {
          if (value < other.value) {
            other.parent = Some(this)
          }
          else {
            parent = Some(other)
          }
        }
      }

      def minValue: Int = getParent.value
    }

    val unimportantReductions: mutable.HashMap[Int, Heap] = mutable.HashMap.empty
    countImportant until allEle.size foreach { l =>
      unimportantReductions += ((l, new Heap(l)))
    }

    def mergeSets(s1: Int, s2: Int) = {
      val m1 = unimportantReductions(s1)
      val m2 = unimportantReductions(s2)
      m1.merge(m2)
    }

    allEle.foreach {
      case (p, n) if (n >= countImportant) => {
        val l = p.getChildren().map(allEle(_)).filter(_ >= countImportant)
        l.foreach(mergeSets(_, n))
      }
      case _ => {}
    }

    val countCircles: mutable.HashMap[Int, Int] = mutable.HashMap.empty
    unimportantReductions foreach { case (v, h) =>
      countCircles += ((h.minValue, countCircles.getOrElse(h.minValue, 0) + 1))
    }

    val nodes = allEle.flatMap { case (p, n) =>
      val label =
        if (n < countImportant)
          eleNames.get(p)
        else
          countCircles.get(n).map(v => s"<internal: $v nodes>")

      val graphType =
        countCircles.get(n) match {
          case Some(x) =>
            Debugger.NodeAggregate(x)
          case None =>
            p match {
              case _: MappedSignal[_, _] =>
                Debugger.MappedSignal
              case _: Variable[_] =>
                Debugger.Var
              case _: ReassignableVariable[_] =>
                Debugger.ReassignableVar
              case _: Signal[_] =>
                Debugger.OtherSignal
              case _: EventSource[_] =>
                Debugger.EventSource
              case _: Event[_] =>
                Debugger.OtherEvent
              case _: Observable =>
                Debugger.Observable
              case _ =>
                Debugger.Other
            }
        }

      label.map { l => GraphNodes(s"D${n}", l, p.name, graphType) }
    }.toSeq

    val edges = allEle.flatMap { case (p, n) =>
      val nn = unimportantReductions.get(n).map(_.minValue).getOrElse(n)
      p.getChildren().map { c =>
        val nc = allEle(c)
        val ncn = unimportantReductions.get(nc).map(_.minValue).getOrElse(nc)
        GraphEdges(s"D${nn}", s"D${ncn}")
      }
    }.toSeq

    Debugger.GraphRepresentation(nodes, edges)
  }
}