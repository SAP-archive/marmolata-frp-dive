package com.sap.marmolata.react.react.impls.selfrx.debugger

import java.time.LocalTime

import com.sap.marmolata.react.api.ReactiveLibrary.Annotateable
import com.sap.marmolata.react.react.debug.{DebugLayer, HasUnderlying}
import com.sap.marmolata.react.react.impls.selfrx
import com.sap.marmolata.react.react.impls.selfrx._

import scala.collection.mutable

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

  override def onNew(u: HasUnderlying[Annotateable]): Unit = {
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
      case Other => "#00FFFF"
      case _: NodeAggregate => "#000000"
    }
  }

  case class NodeAggregate(nrNodes: Int) extends GraphNodeType

  case class GraphNode(id: String, node: NodeDescription) {
    def graphType =
      node match {
        case MultipleNodes(c, _) =>
          Debugger.NodeAggregate(c)
        case MultipleImportantNodes(PrimitiveGroup(_, Seq(p, _*))) =>
          p match {
            case _: MappedSignal[_, _] =>
              Debugger.MappedSignal
            case _: Variable[_] =>
              Debugger.Var
            case _: ReassignableVariable[_] =>
              Debugger.ReassignableVar
            case _: selfrx.Signal[_] =>
              Debugger.OtherSignal
            case _: EventSource[_] =>
              Debugger.EventSource
            case _: selfrx.Event[_] =>
              Debugger.OtherEvent
            case _: Observable =>
              Debugger.Observable
            case _ =>
              Debugger.Other
          }
      }
  }

  sealed trait NodeDescription
  case class MultipleImportantNodes(g: PrimitiveGroup) extends NodeDescription
  case class MultipleNodes(count: Int, p: Seq[Primitive]) extends NodeDescription
  case class SingleNode()

  case class GraphEdges(from: String, to: String)

  case class GraphRepresentation(nodes: Seq[GraphNode], edges: Seq[GraphEdges])

  case class PrimitiveGroup(description: Any, primitives: Seq[Primitive])

  //TODO: there has to be something better in the/some standard library
  final class Heap(private val value: Int, private val prim: Primitive) {
    private var parent: Option[Heap] = None
    private var children: Seq[Heap] = Seq.empty

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
          children = children :+ other
        }
        else {
          parent = Some(other)
          other.children = other.children :+ this
        }
      }
    }

    def minValue: Int = getParent.value

    private def childrenRecursively: Seq[(Int, Primitive)] = {
      (value, prim) +: children.flatMap(_.childrenRecursively)
    }

    def allChildren: Seq[(Int, Primitive)] = {
      getParent.childrenRecursively
    }

    def isMinimum: Boolean = parent.isEmpty
  }

  def calculateGraph(eles: Seq[PrimitiveGroup]): Debugger.GraphRepresentation = {
    val allEle: mutable.HashMap[Primitive, Int] = mutable.HashMap.empty
    var primGroups: Seq[(PrimitiveGroup, Seq[Int])] = Seq.empty
    var toInsert: List[Primitive] = List.empty
    eles foreach { x =>
      var indexes: Seq[Int] = Seq.empty
      x.primitives foreach { y =>
        val index = allEle.getOrElse(y, allEle.size)
        allEle += ((y, allEle.size))
        toInsert = y +: toInsert
        indexes = index +: indexes
      }
      primGroups = ((x, indexes)) +: primGroups
    }

    val countImportant = allEle.size

    while(!toInsert.isEmpty) {
      val next = toInsert.head
      toInsert = toInsert.tail

      (next.getChildren() ++ next.getParents()) foreach { x =>
        if (!allEle.contains(x)) {
          allEle += ((x, allEle.size))
          toInsert = x +: toInsert
        }
      }
    }

    val importantCircles: Map[Int, Int] =
      primGroups.flatMap {
        case (primGroup, indexes) =>
          val head = indexes.head
          indexes.map((_, head))
      }.toMap

    val unimportantReductions: mutable.HashMap[Int, Heap] = mutable.HashMap.empty
    allEle foreach { case (p, i) =>
      if (i >= countImportant) {
        unimportantReductions += ((i, new Heap(i, p)))
      }
    }

    def mergeSets(s1: Int, s2: Int) = {
      val m1 = unimportantReductions(s1)
      val m2 = unimportantReductions(s2)
      m1.merge(m2)
    }

    allEle.foreach {
      case (p, n) =>
        if (n >= countImportant) {
          val l = p.getChildren().map(allEle(_)).filter(_ >= countImportant)
          l.foreach(mergeSets(_, n))
      }
    }

    // go through all heaps and merge the ones with identical (important) input and output
    case class InputAndOutput(input: Set[Int], output: Set[Int])
    val allHeaps: mutable.Map[InputAndOutput, Heap] = mutable.Map.empty

    allEle.foreach {
      case (p, n) =>
        if (n >= countImportant) {
          val heap = unimportantReductions(n)
          val all = heap.allChildren.map(_._2)
          val parents =
            all.flatMap(_.getParents()).map(allEle(_))
              .filter(_ < countImportant)
              .map(y => importantCircles(y)).toSet
          val children =
            all.flatMap(_.getChildren()).map(allEle(_))
              .filter(_ < countImportant)
              .map(y => importantCircles(y)).toSet
          val iao = InputAndOutput(parents, children)
          allHeaps.get(iao) match {
            case None =>
              allHeaps.update(iao, unimportantReductions(n))
            case Some(heap2) =>
              heap merge heap2
          }
        }
    }

    val nodes = allEle.toSeq.flatMap { case (p, n) =>
      val label =
        if (n < countImportant) {
          if (importantCircles(n) == n) {
            Some(MultipleImportantNodes(primGroups.find(x => x._2.head == n).get._1))
          }
          else {
            None
          }
        }
        else {
          val h = unimportantReductions(n)
          if (h.isMinimum) {
            val children = h.allChildren.map(_._2)
            Some(MultipleNodes(children.size, children))
          }
          else {
            None
          }
        }

      label.map { l => GraphNode(s"D${n}", l) }
    }

    val edges = allEle.flatMap { case (p, n) =>
      val nn = unimportantReductions.get(n).map(_.minValue).getOrElse(importantCircles(n))
      p.getChildren().map { c =>
        val nc = allEle(c)
        val ncn = unimportantReductions.get(nc).map(_.minValue).getOrElse(importantCircles(nc))
        (nn, ncn)
        GraphEdges(s"D${nn}", s"D${ncn}")
      }
    }.toSet.toSeq

    Debugger.GraphRepresentation(nodes, edges)
  }
}

class Debugger extends JavaScriptInterface {
  private var elements: mutable.MutableList[Primitive] = mutable.MutableList.empty

  def createPrimitive(p: Primitive): Unit = {
    elements += p
  }

  def allElelements: Seq[Primitive] = elements

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
    val g = currentGraph(_ => true)

    into(s"digraph selfrximage {\n")
    g.nodes.foreach { x => into(s"${x.id} [label=" + "\"" + x.graphType.toString + "\"]\n") }
    g.edges.foreach { x => into(s"${x.from} -> ${x.to}\n") }
    into(s"}\n")
  }

  def currentGraph(filterNodes: Primitive => Boolean): Debugger.GraphRepresentation = {
    Debugger.calculateGraph(elements.filter(filterNodes).map(p => Debugger.PrimitiveGroup("", Seq(p))))
  }
}