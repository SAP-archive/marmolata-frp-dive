package com.sap.marmolata.react.react.impls

package selfrx

import cats.{FlatMap, Monad}
import com.sap.marmolata.react.api.{ReactiveDeclaration, ReactiveLibrary, ReactiveObject}
import com.sap.marmolata.react.react.core.ReactiveLibrary._
import com.sap.marmolata.react.react.core.{ReactiveDeclaration, ReactiveLibrary}
import com.sap.marmolata.react.react.impls.helper.{ReactiveLibraryImplementationHelper, TailRecMImpl}

import scala.collection.immutable.{HashMap, HashSet, SortedMap}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class SelfRxException(message: String) extends Exception(message)

class TriggerUpdate private (recordingSlice: RecordingSliceBuilder) {
  def addEvent[A](event: Event[A], value: A) = {
    currentEvents += ((event, value))
  }

  def getEvent[A](event: Event[A]): Option[A] = {
    currentEvents.get(event).map(_.asInstanceOf[A])
  }

  private var _currentLevel: Int = 0
  private var currentlyEvaluating: SortedMap[Int, HashSet[Primitive]] = SortedMap.empty
  private var currentEvents: HashMap[Primitive, Any] = HashMap.empty
  private var scheduledRemovals: Seq[Function0[Unit]] = Seq.empty

  private[this] var inImmediateInsert: Boolean = false
  def insert(p: Primitive): Unit = {
    if (!inImmediateInsert) {
      if (currentLevel <= p.level) {
        currentlyEvaluating += ((p.level, currentlyEvaluating.getOrElse(p.level, HashSet.empty) + p))
      } else {
        inImmediateInsert = true
        recalculatePrimitive(p)
        inImmediateInsert = false
      }
    }
  }

  def currentLevel: Int = _currentLevel

  def scheduleRemoveParents(f: => Unit): Unit = {
    scheduledRemovals +:= (() => f)
  }

  private[this] def recalculatePrimitive(p: Primitive): Unit = {
    if (recordingSlice.currentRecordingMode == RecordingMode.Record || p.evaluateDuringPlayback()) {
      recordingSlice.aboutToRecalculate(p)
      p.recalculateRecursively(this)
    }
  }

  def evaluateNext(): Boolean = {
    currentlyEvaluating.headOption match {
      case None => false
      case Some((level, elements)) =>
        _currentLevel = level
        currentlyEvaluating = currentlyEvaluating.tail
        elements.foreach { p =>
          if (p.level > level) {
            insert(p)
          }
          else {
            recalculatePrimitive(p)
          }
        }
        true
    }
  }

  def evaluate(): Unit = {
    while (evaluateNext()) { }
    scheduledRemovals foreach { _() }
  }

  def addRecording[A](p: RecordForPlayback[A], before: A, after: A): Unit = {
    recordingSlice.addPrimitiveChange(p, before, after)
  }
}

object TriggerUpdate {
  def doCreatePrimitive(f: TriggerUpdate => Unit)(implicit recording: Recording): Unit = {
    doPrimitiveUpdate { f }
  }

  def doUpdate(recordings: TriggerUpdate => Unit, p: Primitive*)(implicit recording: Recording): Unit = {
    doPrimitiveUpdate { t =>
      recordings(t)
      p.foreach { t.insert(_) }
    }
  }

  def doPrimitiveUpdate(update: TriggerUpdate => Unit)(implicit recording: Recording): Unit = {
    val r = recording.startNewRecording()
    if (r.currentRecordingMode == RecordingMode.Record) {
      try {
        doPrimitiveUpdateUnconditionally(r)(update)
      }
      finally {
        recording.finishCurrentRecording(r)
      }
    }
  }

  def doPrimitiveUpdateUnconditionally(r: RecordingSliceBuilder)(update: TriggerUpdate => Unit): Unit = {
    val triggerUpdate = new TriggerUpdate(r)
    update(triggerUpdate)
    triggerUpdate.evaluate()
  }
}

trait RecordingSliceBuilder {
  def addPrimitiveChange[A](p: RecordForPlayback[A], before: A, after: A)
  def currentRecordingMode: RecordingMode

  def aboutToRecalculate(p: Primitive) = {}
}

trait Recording {
  def startNewRecording(): RecordingSliceBuilder
  def finishCurrentRecording(recording: RecordingSliceBuilder)
}

sealed trait RecordingMode

object RecordingMode {
  object Record extends RecordingMode
  object Playback extends RecordingMode
}


object NoRecordingSlice extends RecordingSliceBuilder {
  override def addPrimitiveChange[A](p: RecordForPlayback[A], before: A, after: A): Unit = {}
  override def currentRecordingMode: RecordingMode = RecordingMode.Record
}

object NoRecording extends Recording {
  override def startNewRecording(): RecordingSliceBuilder = NoRecordingSlice
  override def finishCurrentRecording(recording: RecordingSliceBuilder): Unit = {}
}

trait PrettyPrimitive extends Annotateable {
  self: Primitive =>

  override def toString: String = s"$prettyPrintAnnotations+$level (${super.toString})"
}

// Marker interface for signals that have to record something to be replayed
trait RecordForPlayback[A] {
  this: Primitive =>

  def playback(x: A, strategy: TriggerUpdate)

  def record(before: A, after: A, triggerUpdate: TriggerUpdate): Unit = {
    triggerUpdate.addRecording(this, before, after)
  }
}

trait Primitive extends Object with PrettyPrimitive {
  def becomeOrphan(): Unit
  def getFirstChild(currentTrigger: TriggerUpdate): Unit
  // recalculates this primitive
  // may schedule itself for recalculation (e.g. after other dynamic children have been added and recalculation is needed in any case)
  //  responsible for
  //  - schedule recalculation of children
  def recalculateRecursively(strategy: TriggerUpdate): Unit

  private[this] var _level: Int = 0
  def level: Int = _level
  private[this] def level_=(to: Int): Unit = _level = to

  private[this] var children: HashSet[Primitive] = HashSet.empty
  private[this] var parents: HashSet[Primitive] = HashSet.empty

  final def addParent(p: Primitive, currentTrigger: TriggerUpdate): Unit = {
    if (!(parents contains p)) {

      parents += p
      val minimumLevel = p.addChild(this, currentTrigger)
      if (level <= minimumLevel) {
        incrementLevelAbove(minimumLevel, currentTrigger)
      }
    }
  }

  final def getChildren(): HashSet[Primitive] = children
  final def getParents(): HashSet[Primitive] = parents

  final def replaceParents(currentTrigger: TriggerUpdate, p: Primitive*): Unit = {
    parents.foreach { x =>
      if (!p.contains(x)) {
        x.removeChild(this)
      }
    }

    p.foreach { x =>
      if (!parents.contains(x)) {
        x.addChild(this, currentTrigger)
      }
    }
    parents = HashSet(p: _*)

    val minimumLevel = p.map(_.level).max
    val result = !(level <= minimumLevel)
    if (!result) {
      incrementLevelAbove(minimumLevel, currentTrigger)
    }
  }

  protected def recalculateChildren(strategy: TriggerUpdate): Unit = {
    children.foreach { strategy.insert(_) }
  }

  private def addChild(child: Primitive, currentTrigger: TriggerUpdate): Int = {
    if (children.isEmpty) {
      getFirstChild(currentTrigger)
    }
    children += child
    level
  }

  protected def hasParent(parent: Primitive): Boolean = parents.contains(parent)

  private def removeChild(child: Primitive): Unit = {
    children -= child
    if (children.isEmpty) {
      becomeOrphan()
    }
  }

  private def removeChild(child: Primitive, strategy: TriggerUpdate): Unit = {
    children -= child
    if (children.isEmpty) {
      strategy.scheduleRemoveParents {
        if (children.isEmpty) {
          becomeOrphan()
        }
      }
    }
  }

  protected final def isOrphan: Boolean = children.isEmpty

  protected final def removeParents(): Unit = {
    parents.foreach { _.removeChild(this) }
    parents = HashSet.empty
  }

  private[this] def incrementLevelAbove(newLevel: Int, strategy: TriggerUpdate): Unit = {
    if (newLevel >= level) {
      level = newLevel + 1
      children.foreach { _.incrementLevelAboveOrRemoveParent(level, this, strategy) }
    }
  }

  private def incrementLevelAboveOrRemoveParent(newLevel: Int, parent: Primitive, strategy: TriggerUpdate): Unit = {
    if (newLevel >= level) {
      incrementLevelOrRemoveParent(() => incrementLevelAbove(newLevel, strategy), parent, strategy)
    }
  }

  protected def incrementLevelOrRemoveParent(increment: () => Unit, parent: Primitive, strategy: TriggerUpdate): Unit = {
    increment()
  }

  protected def resetLevel(): Unit = {
    require(parents.isEmpty)
    level = 0
  }

  def evaluateDuringPlayback(): Boolean
}

trait TrackDependency extends TrackDependencyTrait {
  def get[A](s: Signal[A]): A
  def breakLoop(): Unit
}

object TrackDependencyNow extends TrackDependency {
  override def get[A](s: Signal[A]): A = s.now
  override def breakLoop(): Unit = {}
}

sealed trait Estimate[+A] {
  def getOrElse[B >: A](x: =>B): B
  def foreach(x: A => Unit): Unit
}

object Estimate {
  object None extends Estimate[Nothing] {
    override def getOrElse[B >: Nothing](x: => B): B = x
    override def foreach(x: (Nothing) => Unit): Unit = {}
  }

  case class Exact[+A](value: A) extends Estimate[A] {
    override def getOrElse[B >: A](x: => B): B = value
    override def foreach(x: (A) => Unit): Unit = x(value)
  }

  case class Approximately[+A](value: A) extends Estimate[A] {
    override def getOrElse[B >: A](x: => B): B = value
    override def foreach(x: (A) => Unit): Unit = x(value)
  }
}

trait Signal[A] extends Primitive with SignalTrait[A, TrackDependency] {
  type Val = A

  protected def recalculate(recusively: Option[TriggerUpdate]): A

  private var currentValue: Option[A] = None

  override def becomeOrphan(): Unit = {
    removeParents()
    resetLevel()
    currentValue = None
  }

  final protected def updateValueTo(value: A, strategy: TriggerUpdate): Unit = {
    val v = Some(value)
    if (v != currentValue) {
      currentValue = v
      recalculateChildren(strategy)
    }
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    updateValueTo(recalculate(Some(strategy)), strategy)
  }

  def now: A = {
    currentValue.getOrElse {
      val result = recalculate(None)
      if (!isOrphan) {
        currentValue = Some(result)
      }
      result
    }
  }

  // return an estimation of the current value
  // has to return Exact(a) when currentValue is defined
  // otherwise, can either return None or Approximately[anything]
  final def nowEstimate(strategy: TriggerUpdate): Estimate[A] = currentValue match {
    case None => estimate(strategy)
    case Some(x) => Estimate.Exact(x)
  }

  protected[this] def estimate(strategy: TriggerUpdate): Estimate[A] = {
    try {
      Estimate.Approximately(recalculate(None))
    }
    catch {
      case _: Throwable => Estimate.None
    }
  }

  override def observe(f: (A) => Unit): Cancelable = {
    new ObservableSignal(this, f)
  }

  def apply()(implicit td: TrackDependency): A = {
    td.get(this)
  }

  override def evaluateDuringPlayback(): Boolean = true
}

trait RecalculateNow[A] {
  self: Signal[A] =>
  override protected def recalculate(recursively: Option[TriggerUpdate]): A = {
    require(recursively.isEmpty, "recalculateRecursively doesn't call recalculate")
    calculateNow
  }

  // this has to be overwritten (scala doesn't allow to make a def abstract again unfortunately)
  override def recalculateRecursively(strategy: TriggerUpdate): Unit

  protected[this] def calculateNow: A
}

trait Observable extends Annotateable with Cancelable

class ObservableSignal[A](s: Signal[A], f: A => Unit) extends Primitive with Observable {
  TriggerUpdate.doCreatePrimitive { tu =>
    addParent(s, tu)
    tu.insert(this)
  }(NoRecording)

  override def becomeOrphan(): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    f(s.now)
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}

  def kill(): Unit = removeParents()

  override def evaluateDuringPlayback(): Boolean = true
}

class ObservableEvent[A](e: Event[A], f: A => Unit) extends Primitive with Observable {
  TriggerUpdate.doCreatePrimitive {
    addParent(e, _)
  }(NoRecording)

  override def becomeOrphan(): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    f(strategy.getEvent(e).getOrElse {
      throw new SelfRxException(("observe triggered but no underlying event"))
    })
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}

  def kill(): Unit = removeParents()

  override def evaluateDuringPlayback(): Boolean = false
}

class Variable[A](var init: A)(implicit recording: Recording) extends Signal[A] with VarTrait[A, TrackDependency] with RecordForPlayback[A] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): A = init

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}

  override def update(newVal: A): Unit = {
    val last = init
    init = newVal
    if (last != init) {
      TriggerUpdate.doUpdate(record(last, newVal, _), this)
    }
  }

  override def playback(x: A, strategy: TriggerUpdate): Unit = { init = x; updateValueTo(init, strategy) }
}

//TODO: record for playback only when subscribe/update
class ReassignableVariable[A](var init2: Signal[_ <: A])(implicit recording: Recording)
  extends Signal[A] with ReassignableVarTrait[A, ({type L[A] = Signal[_ <: A]})#L, TrackDependency]
  with RecordForPlayback[Signal[_ <: A]] {
  override def update(newValue: A): Unit = subscribe(new ConstSignal(newValue))

  def subscribe(s: Signal[_ <: A]): Unit = {
    val oldInit2 = init2
    init2 = s

    if (!isOrphan) {
      //TODO: decide if this should be merged to one TriggerUpdate call
      TriggerUpdate.doCreatePrimitive(replaceParents(_, init2))
      TriggerUpdate.doUpdate(record(oldInit2, init2, _), this)
    }
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(init2, currentTrigger)
    currentTrigger.insert(this)
  }

  override protected def recalculate(recusively: Option[TriggerUpdate]): A =
    init2.now

  override def toSignal: Signal[A] = this

  override def playback(x: Signal[_ <: A], strategy: TriggerUpdate): Unit = {
    init2 = x
    if (!isOrphan) {
      // FIXME: uncomment next line (?)
      // replaceParents(None, init2)
    }
    updateValueTo(init2.now, strategy)
  }
}

class DynamicSignal[A](formula: TrackDependency => A) extends Signal[A] {
  self =>
  private object DynamicSignalException extends SelfRxException(s"internal Dynamic exception $self")

  private[this] var firstParent: Option[Signal[_]] = None

  override def becomeOrphan(): Unit = {
    super.becomeOrphan()
    firstParent = None
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    var allDependends: Seq[Primitive] = Seq.empty
    var stillExact: Boolean = true
    val td = new TrackDependency {
      override def get[A](s: Signal[A]): A = {
        allDependends +:= s
        if (s.level >= level) {
          stillExact = false
        }
        s.nowEstimate(strategy).getOrElse(throw DynamicSignalException)
      }

      override def breakLoop(): Unit = {
        if (!stillExact)
          throw DynamicSignalException
      }
    }
    val result: Option[A] =
      try {
        Some(formula(td))
      } catch {
        case x: Throwable =>
          if (stillExact) {
            throw x
          } else {
            None
          }
      }
    replaceParents(strategy, allDependends: _*)
    if (level == strategy.currentLevel) {
      updateValueTo(result.getOrElse(throw new SelfRxException("internal error")), strategy)
    }
    else {
      strategy.insert(this)
    }
  }

  override protected def incrementLevelOrRemoveParent(increment: () => Unit, parent: Primitive, strategy: TriggerUpdate): Unit = {
    if (firstParent.contains(parent)) {
      increment()
    } else {
      // remove parents to be on the safe side of not introducing a circular dependency,
      // but increment level nonetheless to avoid too many recalculations
      increment()
      replaceParents(strategy, firstParent.toList: _*)
    }
  }

  override protected def recalculate(recusively: Option[TriggerUpdate]): A = {
    require(recusively.isEmpty, "recalculateRecursively shouldn't call this")
    formula(TrackDependencyNow)
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    firstParent = None
    val td = new TrackDependency {
      override def get[A](s: Signal[A]): A = {
        addParent(s, currentTrigger)
        if (firstParent.isEmpty) firstParent = Some(s)
        s.nowEstimate(currentTrigger).getOrElse(throw DynamicSignalException)
      }
      override def breakLoop(): Unit = throw DynamicSignalException
    }
    try {
      formula(td)
    }
    catch {
      case _: Throwable => ()
    }
    currentTrigger.insert(this)
  }

  override protected[this] def estimate(strategy: TriggerUpdate): Estimate[A] = {
    try {
      val result = formula {
        new TrackDependency {
          override def breakLoop(): Unit = throw DynamicSignalException

          override def get[A](s: Signal[A]): A =
            s.nowEstimate(strategy).getOrElse(throw DynamicSignalException)
        }
      }
      Estimate.Approximately(result)
    }
    catch {
      case _: Throwable =>
        Estimate.None
    }
  }
}

class MappedSignal[A, B](s: Signal[_ <: A], f: A => B) extends Signal[B] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): B = {
    f(s.now)
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(s, currentTrigger)
    currentTrigger.insert(this)
  }
}

class ProductSignal[A, B](a: Signal[_ <: A], b: Signal[_ <: B]) extends Signal[(A, B)] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): (A, B) = {
    (a.now, b.now)
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(a, currentTrigger)
    addParent(b, currentTrigger)
    currentTrigger.insert(this)
  }
}

class ConstSignal[A](value: A) extends Signal[A] {
  override protected def recalculate(recusively: Option[TriggerUpdate]): A = value

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}
}

class BindSignal[A](a: Signal[_ <: Signal[_ <: A]]) extends Signal[A] with RecalculateNow[A] {
  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val newSignal = a.now
    replaceParents(strategy, a, newSignal)
    if (strategy.currentLevel == level) {
      updateValueTo(newSignal.now, strategy)
    }
  }

  override protected[this] def calculateNow: A = a.now.now

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(a, currentTrigger)
    a.nowEstimate(currentTrigger).foreach {
      addParent(_, currentTrigger)
    }
    currentTrigger.insert(this)
  }

  override protected[this] def incrementLevelOrRemoveParent(incrementLevel: () => Unit, parent: Primitive, strategy: TriggerUpdate): Unit = {
    if (parent == a) {
      incrementLevel()
    } else {
      replaceParents(strategy, a)
    }
  }
}

trait Event[A] extends Primitive with EventTrait[A] {
  type Val = A
  override def becomeOrphan(): Unit = {
    removeParents()
    resetLevel()
  }

  override def observe(f: (A) => Unit): Cancelable = {
    new ObservableEvent(this, f)
  }

  override def evaluateDuringPlayback(): Boolean = false
}

class EventSource[A](implicit recording: Recording) extends Event[A] with EventSourceTrait[A] {
  override def emit(value: A): Unit = {
    val triggerEvent = TriggerUpdate.doPrimitiveUpdate { triggerEvent =>
      triggerEvent.insert(this)
      triggerEvent.addEvent(this, value)
    }
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    recalculateChildren(strategy)
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}
}

class MappedEvent[A, B](a: Event[_ <: A], f: A => B) extends Event[B] {
  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(a, currentTrigger)
    currentTrigger.insert(this)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.getEvent(a).foreach { ev =>
      strategy.addEvent(this, f(ev))
      recalculateChildren(strategy)
    }
  }
}

class FutureEvent[A](f: Future[A])(implicit ec: ExecutionContext, recording: Recording) extends Event[A] {
  f.onSuccess { case value =>
    val triggerEvent = TriggerUpdate.doPrimitiveUpdate { triggerEvent =>
      triggerEvent.insert(this)
      triggerEvent.addEvent(this, value)
    }
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    recalculateChildren(strategy)
  }
}

class MergeEvent[A](e1: Event[_ <: A], e2: Event[_ <: A]) extends Event[A] {
  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(e1, currentTrigger)
    addParent(e2, currentTrigger)
    currentTrigger.insert(this)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val o1 = strategy.getEvent(e1)
    val o2 = strategy.getEvent(e2)
    val e: A = (o1, o2) match {
      case (Some(x), _) => x
      case (None, Some(y)) => y
      case (None, None) => return
    }
    strategy.addEvent(this, e)
    recalculateChildren(strategy)
  }
}

class FilterEvent[A](e: Event[A], filter: A => Boolean) extends Event[A] {
  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(e, currentTrigger)
    currentTrigger.insert(this)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.getEvent(e).foreach { o =>
      if (filter(o)) {
        strategy.addEvent(this, o)
        recalculateChildren(strategy)
      }
    }
  }
}

class TriggerWhenEvent[A, B, C](e: Event[A], s: Signal[B], f: (A, B) => C) extends Event[C] {
  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(e, currentTrigger)
    addParent(s, currentTrigger)
    currentTrigger.insert(this)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.getEvent(e).foreach { ev =>
      strategy.addEvent(this, f(ev, s.now))
      recalculateChildren(strategy)
    }
  }
}

object Never extends Event[Nothing] {
  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {}
}


// TODO: this is generally leaking, how could we avoid this?
// (this may be possible with caching event values recursively)
class SignalFromEvent[A](a: Event[_ <: A], init: A) extends Signal[A] with RecordForPlayback[A] {
  //since Events don't cache their events, but Signals do, we have to trigger this event
  //even if it isn't listened to currently
  override def becomeOrphan(): Unit = {}

  TriggerUpdate.doCreatePrimitive {
    addParent(a, _)
  }(NoRecording)

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val newValue = recalculate(Some(strategy))
    val lastValue = now
    if (newValue != lastValue) {
      record(lastValue, newValue, strategy)
    }
    updateValueTo(newValue, strategy)
  }

  override protected def recalculate(recursively: Option[TriggerUpdate]): A = {
    recursively match {
      case None => init
      case Some(t) => t.getEvent(a).getOrElse(throw new SelfRxException("event is not trigger, but it should have been"))
    }
  }

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}

  override def playback(x: A, strategy: TriggerUpdate): Unit = {
    updateValueTo(x, strategy)
  }
}

class FoldSignal[A, B](a: Event[_ <: A], init: B, fun: (A, B) => B)(implicit recording: Recording) extends Signal[B] with RecordForPlayback[B] {
  override def becomeOrphan(): Unit = {}

  TriggerUpdate.doCreatePrimitive {
    addParent(a, _)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val ev = strategy.getEvent(a).getOrElse(throw new SelfRxException("event is not triggered, but it should have been (FoldSignal)"))
    val lastValue = now
    val newValue = fun(ev, lastValue)
    if (newValue != lastValue) {
      record(lastValue, newValue, strategy)
    }
    updateValueTo(newValue, strategy)
  }

  override protected def recalculate(recusively: Option[TriggerUpdate]): B = init

  override def playback(x: B, strategy: TriggerUpdate): Unit =
    updateValueTo(x, strategy)

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}
}

class EventFromSignal[A](s: Signal[_ <: A]) extends Event[A] {
  // you would think that this only needs to track its signal when its used
  // but the problem is that the signal forgets if it was changed immediately
  // after it changed,
  // so when this event only comes to life later (but in the same turn)
  // we may not be able to know if we should immediately trigger
  // QUESTION: is it even possible that an event comes to live in this condition?
  // (haven't been able to construct a test case)
  TriggerUpdate.doCreatePrimitive(addParent(s, _))(NoRecording)

  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.addEvent(this, s.now)
    recalculateChildren(strategy)
  }

  override def becomeOrphan(): Unit = {}
}

class EventInsideSignal[A](s: Signal[_ <: Event[_ <: A]]) extends Event[A] {
  override def getFirstChild(currentTrigger: TriggerUpdate): Unit = {
    addParent(s, currentTrigger)
    addParent(s.now, currentTrigger)
    currentTrigger.insert(this)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val newEvent = s.now
    if (hasParent(newEvent)) {
      val event = strategy.getEvent(newEvent)
      event foreach { x =>
        strategy.addEvent(this, x)
        recalculateChildren(strategy)
      }
    }
    else {
      replaceParents(strategy, s, newEvent)
      strategy.insert(this)
    }
  }
}

trait SelfRxImpl extends ReactiveLibrary with ReactiveLibraryImplementationHelper {
  self =>
  implicit def recording: Recording = NoRecording

  override type Event[+A] = selfrx.Event[_ <: A]
  override type Signal[+A] = selfrx.Signal[_ <: A]
  override type Var[A] = Variable[A]
  override type EventSource[A] = selfrx.EventSource[A]

  override type ReassignableVar[A] = ReassignableVariable[A]
  override object ReassignableVar extends ReassignableVarCompanionObject[ReassignableVar, Signal] {
    override def apply[A](init: A): ReassignableVariable[A] = new ReassignableVariable[A](Signal.Const(init))

    override def apply[A](init: selfrx.Signal[_ <: A]): ReassignableVariable[A] = new ReassignableVariable[A](init)
  }

  override def toSignal[A](init: A, event: Event[A]): Signal[A] = {
    new SignalFromEvent(event, init)
  }
  override def futureToEvent[A](f: Future[A])(implicit ec: ExecutionContext): self.Event[A] =
    new FutureEvent(f)

  override def triggerWhen[A, B, C](s: Signal[A], e: Event[B], f: (A, B) => C): Event[C] =
    new TriggerWhenEvent(e, s, (x: B, y: A) => f(y, x))

  override def fold[A, B](e: Event[A], init: B, fun: (A, B) => B): Signal[B] =
    new FoldSignal(e, init, fun)

  override def signalToTry[A](s: Signal[A]): Signal[Try[A]] = {
    new MappedSignal[A, Try[A]](s, Success(_))
  }

  override def implementationName: String = "selfrx implementation"

  override def toEvent[A](signal: Signal[A]): Event[A] =
    new EventFromSignal(signal)

  override def flattenEvents[A](s: Signal[Event[A]]): Event[A] = {
    new EventInsideSignal[A](s)
  }

  override implicit object marmolataDiveEventTypeclass extends EventOperationsTrait[Event] {
    override def merge[A](x1: Event[A], x2: Event[A]): selfrx.Event[A] =
      new MergeEvent[A](x1, x2)

    override def map[A, B](fa: Event[A])(f: (A) => B): selfrx.Event[B] =
      new MappedEvent(fa, f)

    override def filter[A](v: Event[A], cond: (A) => Boolean): Event[A] =
      new FilterEvent(v, cond)
  }

  override implicit object Var extends VarCompanionObject[Var] {
    override def apply[A](init: A): Variable[A] = {
      val result = new Variable(init)
      result
    }
  }

  object unsafeImplicits extends UnsafeImplicits {
    override implicit val marmolataDiveSignalTypeclass: Monad[Signal] with SignalOperationsTrait[Signal] = self.marmolataDiveSignalTypeclass
  }

  override object EventSource extends EventSourceCompanionObject[Event, EventSource] {
    override def apply[A](): EventSource[A] = {
      val result = new selfrx.EventSource[A]()
      result
    }
  }

  override implicit object marmolataDiveSignalTypeclass extends Monad[Signal] with SignalOperationsTrait[Signal] with TailRecMImpl[Signal] {
    override def pure[A](x: A): selfrx.Signal[A] = new ConstSignal(x)

    override def ap[A, B](ff: Signal[(A) => B])(fa: Signal[A]): Signal[B] =
      new MappedSignal(new ProductSignal(ff, fa), { (v: (A => B, A)) => v._1(v._2) })


    override def map[A, B](fa: selfrx.Signal[_ <: A])(f: (A) => B): selfrx.Signal[_ <: B] =
      new MappedSignal(fa, f)

    def flatMap[A, B](fa: Signal[A])(f: (A) => Signal[B]): Signal[B] = {
      new BindSignal[B](new MappedSignal[A, selfrx.Signal[_ <: B]](fa, f))
    }

    override def flatten[A](ffa: selfrx.Signal[_ <: selfrx.Signal[_ <: A]]): selfrx.Signal[_ <: A] = {
      new BindSignal[A](ffa)
    }

    override def product[A, B](fa: Signal[A], fb: Signal[B]): Signal[(A, B)] =
      new ProductSignal[A, B](fa, fb)
  }

  override object Signal extends SignalCompanionObject[Signal, TrackDependency] {
    override def Const[A](value: A): selfrx.Signal[A] = new ConstSignal(value)

    def apply[A](fun: TrackDependency => A): Signal[A] = {
      new DynamicSignal[A](fun)
    }

    def breakPotentiallyLongComputation()(implicit td: TrackDependency): Unit = {
      td.breakLoop()
    }
  }

  object Event extends EventCompanionObject[Event] {
    override def Never: selfrx.Event[Nothing] = selfrx.Never
  }

  override type TrackDependency = selfrx.TrackDependency
}

final class ReactiveObject extends ReactiveObject {
  val library: ReactiveDeclaration = new SelfRxImpl with ReactiveDeclaration
}