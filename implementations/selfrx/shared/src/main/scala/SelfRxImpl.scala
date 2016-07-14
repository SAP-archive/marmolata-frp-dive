package reactive.selfrx

import java.time.LocalDateTime

import cats.{Monad, Apply, FlatMap}
import react.{ReactiveDeclaration, ReactiveLibrary}
import react.ReactiveLibrary._
import react.impls.helper.{DefaultReassignableVar, ReactiveLibraryImplementationHelper}
import reactive.selfrx

import scala.collection.immutable.{SortedMap, HashMap, HashSet}
import scala.concurrent.{ExecutionContext, Future}

class SelfRxException(message: String) extends Exception(message)

class TriggerUpdate private (recordingSlice: RecordingSliceBuilder) {
  def addEvent[A](event: Event[A], value: A) = {
    currentEvents += ((event, value))
  }

  def getEvent[A](event: Event[A]): Option[A] = {
    currentEvents.get(event).map(_.asInstanceOf[A])
  }

  var currentlyEvaluating: SortedMap[Int, HashSet[Primitive]] = SortedMap.empty
  var finishedEvaluating: HashSet[Primitive] = HashSet.empty
  var currentEvents: HashMap[Primitive, Any] = HashMap.empty

  def insert(p: Primitive): Unit = {
    currentlyEvaluating += ((p.level, currentlyEvaluating.getOrElse(p.level, HashSet.empty) + p))
  }

  def evaluateNext(): Boolean = {
    currentlyEvaluating.headOption match {
      case None => false
      case Some((level, elements)) =>
        currentlyEvaluating = currentlyEvaluating.tail
        elements.foreach { p =>
          if (p.level > level) {
            insert(p)
          }
          else {
            if (recordingSlice.currentRecordingMode == RecordingMode.Record || p.evaluateDuringPlayback()) {
              p.recalculateRecursively(this)
              finishedEvaluating += p
            }
          }
        }
        true
    }
  }

  def evaluate(): Unit = {
    while (evaluateNext()) { }
  }

  def finishedRecalculating(p: Primitive): Boolean = {
    finishedEvaluating.contains(p)
  }

  def addRecording[A](p: RecordForPlayback[A], before: A, after: A): Unit = {
    recordingSlice.addPrimitiveChange(p, before, after)
  }
}

object TriggerUpdate {
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
  def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit
  def recalculateRecursively(strategy: TriggerUpdate): Unit
  def evaluateDuringPlayback(): Boolean

  var level: Int = 0
  private var children: HashSet[Primitive] = HashSet.empty
  private var parents: HashSet[Primitive] = HashSet.empty

  final def addParent(p: Primitive, currentTrigger: Option[TriggerUpdate]): Unit = {
    if (!(parents contains p)) {
      var shouldRecalculate = false

      parents += p
      val minimumLevel = p.addChild(this, currentTrigger)
      if (level <= minimumLevel) {
        incrementLevelAbove(minimumLevel)
      }

      currentTrigger.foreach { t =>
        if (t.finishedRecalculating(p) || shouldRecalculate) {
          t.insert(this)
        }
      }
    }
  }

  final def getChildren(): HashSet[Primitive] = children
  final def getParents(): HashSet[Primitive] = parents

  final def replaceParents(currentTrigger: Option[TriggerUpdate], p: Primitive*): Unit = {
    var shouldRecalculate = false

    parents.foreach { x =>
      if (!p.contains(x)) {
        x.removeChild(this)
      }
    }
    p.foreach { x =>
      if (!parents.contains(x)) {
        x.addChild(this, currentTrigger)
      }
      currentTrigger foreach { t =>
        if (t.finishedRecalculating(x)) {
          shouldRecalculate = true
        }
      }
    }
    parents = HashSet(p: _*)

    val minimumLevel = p.map(_.level).max
    if (level <= minimumLevel) {
      incrementLevelAbove(minimumLevel)
      shouldRecalculate = true
    }

    if (shouldRecalculate) {
      currentTrigger foreach {
        _.insert(this)
      }
    }
  }

  protected def recalculateChildren(strategy: TriggerUpdate): Unit = {
    children.foreach { strategy.insert(_) }
  }

  private def addChild(child: Primitive, currentTrigger: Option[TriggerUpdate]): Int = {
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

  protected final def isOrphan: Boolean = children.isEmpty

  protected final def removeParents(): Unit = {
    parents.foreach { _.removeChild(this) }
    parents = HashSet.empty
  }

  private def incrementLevelAbove(newLevel: Int): Unit = {
    if (newLevel >= level) {
      level = newLevel + 1
      children.foreach { _.incrementLevelAbove(level) }
    }
  }

  protected def resetLevel(): Unit = {
    assert(parents.isEmpty)
    level = 0
  }
}

trait Signal[A] extends Primitive with SignalTrait[A] {
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

  override def observe(f: (A) => Unit): Cancelable = {
    val obs = new ObservableSignal(this, f)
    f(now)
    new ObservableCancel(obs)
  }

  override def evaluateDuringPlayback(): Boolean = true
}

trait Observable extends Annotateable {
  def kill(): Unit
}

class ObservableSignal[A](s: Signal[A], f: A => Unit) extends Primitive with Observable {
  addParent(s, None)

  override def becomeOrphan(): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    f(s.now)
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  def kill(): Unit = removeParents()

  override def evaluateDuringPlayback(): Boolean = true
}

class ObservableEvent[A](e: Event[A], f: A => Unit) extends Primitive with Observable {
  addParent(e, None)

  override def becomeOrphan(): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    f(strategy.getEvent(e).getOrElse {
      throw new SelfRxException(("observe triggered but no underlying event"))
    })
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  def kill(): Unit = removeParents()

  override def evaluateDuringPlayback(): Boolean = false
}

class Variable[A](var init: A)(implicit recording: Recording) extends Signal[A] with VarTrait[A] with RecordForPlayback[A] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): A = init

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

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
  extends Signal[A] with ReassignableVarTrait[A, ({type L[A] = Signal[_ <: A]})#L]
  with RecordForPlayback[Signal[_ <: A]] {
  override def update(newValue: A): Unit = subscribe(new ConstSignal(newValue))

  def subscribe(s: Signal[_ <: A]): Unit = {
    val oldInit2 = init2
    init2 = s

    if (!isOrphan) {
      replaceParents(None, init2)
    }
    TriggerUpdate.doUpdate(record(oldInit2, init2, _), this)
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(init2, currentTrigger)
  }

  override protected def recalculate(recusively: Option[TriggerUpdate]): A =
    init2.now

  override def toSignal: Signal[A] = this

  override def playback(x: Signal[_ <: A], strategy: TriggerUpdate): Unit = {
    init2 = x
    if (!isOrphan) {
      replaceParents(None, init2)
    }
    updateValueTo(init2.now, strategy)
  }
}

class ObservableCancel(obs: Observable) extends Cancelable {
  override def kill(): Unit = obs.kill()

  override def addAnnotation(annotation: Annotation): Unit = obs.tag(annotation)
  override def allAnnotations: Seq[Annotation] = obs.allAnnotations
}

class MappedSignal[A, B](s: Signal[_ <: A], f: A => B) extends Signal[B] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): B = {
    f(s.now)
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(s, currentTrigger)
  }
}

class ProductSignal[A, B](a: Signal[_ <: A], b: Signal[_ <: B]) extends Signal[(A, B)] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): (A, B) = {
    (a.now, b.now)
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(a, currentTrigger)
    addParent(b, currentTrigger)
  }
}

class ConstSignal[A](value: A) extends Signal[A] {
  override protected def recalculate(recusively: Option[TriggerUpdate]): A = value

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}
}

class BindSignal[A](a: Signal[_ <: Signal[_ <: A]]) extends Signal[A] {
  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val newSignal = a.now
    if (hasParent(newSignal)) {
      updateValueTo(newSignal.now, strategy)
    }
    else {
      replaceParents(Some(strategy), a, newSignal)
    }
  }

  override protected def recalculate(recursively: Option[TriggerUpdate]): A = {
    // we overwrite recalculateRecursively, so this should only be called by None
    assert(recursively.isEmpty)
    a.now.now
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(a, currentTrigger)
    addParent(a.now, currentTrigger)
  }
}

trait Event[A] extends Primitive with EventTrait[A] {
  type Val = A
  override def becomeOrphan(): Unit = {
    removeParents()
    resetLevel()
  }

  override def observe(f: (A) => Unit): Cancelable = {
    val obs = new ObservableEvent(this, f)
    new ObservableCancel(obs)
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

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}
}

class MappedEvent[A, B](a: Event[_ <: A], f: A => B) extends Event[B] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(a, currentTrigger)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.addEvent(this, f(strategy.getEvent(a).getOrElse { throw new SelfRxException("recalculate without event")}))
    recalculateChildren(strategy)
  }
}

class FutureEvent[A](f: Future[A])(implicit ec: ExecutionContext, recording: Recording) extends Event[A] {
  f.onSuccess { case value =>
    val triggerEvent = TriggerUpdate.doPrimitiveUpdate { triggerEvent =>
      triggerEvent.insert(this)
      triggerEvent.addEvent(this, value)
    }
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    recalculateChildren(strategy)
  }
}

class MergeEvent[A](e1: Event[_ <: A], e2: Event[_ <: A]) extends Event[A] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(e1, currentTrigger)
    addParent(e2, currentTrigger)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val o1 = strategy.getEvent(e1)
    val o2 = strategy.getEvent(e2)
    val e: A = (o1, o2) match {
      case (Some(x), _) => x
      case (None, Some(y)) => y
      case (None, None) => throw new SelfRxException("recalculate without event in merge")
    }
    strategy.addEvent(this, e)
    recalculateChildren(strategy)
  }
}

class FilterEvent[A](e: Event[A], filter: A => Boolean) extends Event[A] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(e, currentTrigger)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val o = strategy.getEvent(e).getOrElse(throw new SelfRxException("recalculate witjout event in filter"))
    if (filter(o)) {
      strategy.addEvent(this, o)
      recalculateChildren(strategy)
    }
  }
}

class TriggerWhenEvent[A, B, C](e: Event[A], s: Signal[B], f: (A, B) => C) extends Event[C] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(e, currentTrigger)
    addParent(s, currentTrigger)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.getEvent(e).foreach { ev =>
      strategy.addEvent(this, f(ev, s.now))
      recalculateChildren(strategy)
    }
  }
}

object Never extends Event[Nothing] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {}
}


// TODO: this is generally leaking, how could we avoid this?
// (this may be possible with caching event values recursively)
class SignalFromEvent[A](a: Event[_ <: A], init: A) extends Signal[A] with RecordForPlayback[A] {
  //since Events don't cache their events, but Signals do, we have to trigger this event
  //even if it isn't listened to currently
  override def becomeOrphan(): Unit = {}

  addParent(a, None)

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

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  override def playback(x: A, strategy: TriggerUpdate): Unit = {
    updateValueTo(x, strategy)
  }
}

class EventFromSignal[A](s: Signal[_ <: A]) extends Event[A] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(s, currentTrigger)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.addEvent(this, s.now)
    recalculateChildren(strategy)
  }
}

class EventInsideSignal[A](s: Signal[Event[_ <: A]]) extends Event[A] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(s, currentTrigger)
    addParent(s.now, currentTrigger)
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
      replaceParents(Some(strategy), s, newEvent)
    }
  }
}

trait SelfRxImpl extends ReactiveLibrary with ReactiveLibraryImplementationHelper {
  self =>
  implicit def recording: Recording = NoRecording

  override type Event[+A] = reactive.selfrx.Event[_ <: A]
  override type Signal[+A] = reactive.selfrx.Signal[_ <: A]
  override type Var[A] = reactive.selfrx.Variable[A]
  override type EventSource[A] = reactive.selfrx.EventSource[A]

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

  override def implementationName: String = "selfrx implementation"

  override def toEvent[A](signal: Signal[A]): Event[A] =
    new EventFromSignal(signal)

  override implicit object eventApplicative extends EventOperationsTrait[Event] {
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
    override implicit object eventApplicative extends FlatMap[Event] with EventOperationsTrait[Event] {
      override def flatMap[A, B](fa: Event[A])(f: (A) => Event[B]): Event[B] = {
        val result: selfrx.Signal[Event[B]] =
          new SignalFromEvent[Event[B]](new MappedEvent[A, Event[B]](fa, f), selfrx.Never)
        new EventInsideSignal[B](result)
      }

      override def flatten[A](ffa: selfrx.Event[_ <: selfrx.Event[_ <: A]]): selfrx.Event[_ <: A] = {
        new EventInsideSignal[A](
          new SignalFromEvent[Event[A]](ffa, selfrx.Never)
        )
      }

      override def map[A, B](fa: selfrx.Event[_ <: A])(f: (A) => B): selfrx.Event[_ <: B] = {
        self.eventApplicative.map(fa)(f)
      }

      override def filter[A](v: selfrx.Event[_ <: A], cond: (A) => Boolean): selfrx.Event[_ <: A] =
        self.eventApplicative.filter(v, cond)

      override def merge[A](x1: selfrx.Event[_ <: A], x2: selfrx.Event[_ <: A]): selfrx.Event[_ <: A] =
        self.eventApplicative.merge(x1, x2)
    }
    override implicit val signalApplicative: Monad[Signal] with SignalOperationsTrait[Signal] = self.signalApplicative
  }

  override object EventSource extends EventSourceCompanionObject[Event, EventSource] {
    override def apply[A](): EventSource[A] = {
      val result = new selfrx.EventSource[A]()
      result
    }
  }

  override implicit object signalApplicative extends Monad[Signal] with SignalOperationsTrait[Signal] {
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

  override object Signal extends SignalCompanionObject[Signal] {
    override def Const[A](value: A): selfrx.Signal[A] = new ConstSignal(value)
  }

  object Event extends EventCompanionObject[Event] {
    override def Never: selfrx.Event[Nothing] = selfrx.Never
  }
}

final class ReactiveObject extends react.ReactiveObject {
  val library: ReactiveDeclaration = new SelfRxImpl with ReactiveDeclaration
}