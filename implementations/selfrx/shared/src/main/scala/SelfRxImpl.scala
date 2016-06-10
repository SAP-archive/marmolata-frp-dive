package reactive.selfrx

import cats.{Apply, FlatMap}
import react.ReactiveLibrary
import react.ReactiveLibrary._
import react.impls.helper.ReactiveLibraryImplementationHelper
import reactive.selfrx

import scala.collection.immutable.{HashMap, HashSet}
import scala.concurrent.{ExecutionContext, Future}

class SelfRxException(message: String) extends Exception(message)


trait SelfRxLogging {
  def createPrimitive(p: Primitive)
}

trait SelfRxLoggingHelper {
  val logger: SelfRxLogging
}

object NoLogging extends SelfRxLogging {
  override def createPrimitive(p: Primitive): Unit = {}
}

class TriggerUpdate {
  def addEvent[A](event: Event[A], value: A) = {
    currentEvents += ((event, value))
  }

  def getEvent[A](event: Event[A]): Option[A] = {
    currentEvents.get(event).map(_.asInstanceOf[A])
  }

  var currentlyEvaluating: Map[Int, HashSet[Primitive]] = Map.empty
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
            p.recalculateRecursively(this)
            finishedEvaluating += p
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
}

object TriggerUpdate {
  def doUpdate(p: Primitive*): Unit = {
    val t = new TriggerUpdate()
    p.foreach { t.insert(_) }
    t.evaluate()
  }
}

trait Primitive extends Object {
  def becomeOrphan(): Unit
  def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit
  def recalculateRecursively(strategy: TriggerUpdate): Unit

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
}

trait Observable {
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
}

class Variable[A](var init: A) extends Signal[A] with VarTrait[A] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): A = init

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  override def update(newVal: A): Unit = {
    init = newVal
    TriggerUpdate.doUpdate(this)
  }
}

class ReassignableVariable[A](var init2: Signal[A]) extends Signal[A] with VarTrait[A] {

  override def update(newValue: A): Unit = subscribe(new ConstSignal(newValue))

  def subscribe(s: Signal[A]): Unit = {
    init2 = s

    if (!isOrphan) {
      replaceParents(None, init2)
      TriggerUpdate.doUpdate(this)
    }
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(init2, currentTrigger)
  }

  override protected def recalculate(recusively: Option[TriggerUpdate]): A =
    init2.now
}

class ObservableCancel(obs: Observable) extends Cancelable {
  override def kill(): Unit = obs.kill()
}

class MappedSignal[A, B](s: Signal[A], f: A => B) extends Signal[B] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): B = {
    f(s.now)
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(s, currentTrigger)
  }

  //override def toString: String = s"MappedSignal($s, $f) ${super.toString}"
}

class ProductSignal[A, B](a: Signal[A], b: Signal[B]) extends Signal[(A, B)] {
  override protected def recalculate(recursively: Option[TriggerUpdate]): (A, B) = {
    (a.now, b.now)
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(a, currentTrigger)
    addParent(b, currentTrigger)
  }

  //override def toString: String = s"ProductSignal($a, $b) ${super.toString}"
}

class ConstSignal[A](value: A) extends Signal[A] {
  override protected def recalculate(recusively: Option[TriggerUpdate]): A = value

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  //override def toString: String = s"ConstSignal($value) ${super.toString}"
}

class BindSignal[A](a: Signal[Signal[A]]) extends Signal[A] {
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
  }

  //override def toString: String = s"BindSignal($a) ${super.toString}"
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
}

class EventSource[A] extends Event[A] with EventSourceTrait[A] {
  override def emit(value: A): Unit = {
    val triggerEvent = new TriggerUpdate()
    triggerEvent.insert(this)
    triggerEvent.addEvent(this, value)
    triggerEvent.evaluate()
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    recalculateChildren(strategy)
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}
}

class MappedEvent[A, B](a: Event[A], f: A => B) extends Event[B] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(a, currentTrigger)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.addEvent(this, f(strategy.getEvent(a).getOrElse { throw new SelfRxException("recalculate without event")}))
    recalculateChildren(strategy)
  }
}

class FutureEvent[A](f: Future[A])(implicit ec: ExecutionContext) extends Event[A] {
  f.onSuccess { case value =>
    val triggerEvent = new TriggerUpdate()
    triggerEvent.insert(this)
    triggerEvent.addEvent(this, value)
    triggerEvent.evaluate()
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    recalculateChildren(strategy)
  }
}

class MergeEvent[A](e1: Event[A], e2: Event[A]) extends Event[A] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(e1, currentTrigger)
    addParent(e2, currentTrigger)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    val o1 = strategy.getEvent(e1)
    val o2 = strategy.getEvent(e2)
    val e = (o1, o2) match {
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

class NeverG[A] extends Event[A] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {}
}

// TODO: this is generally leaking, how could we avoid this?
class SignalFromEvent[A](a: Event[A], init: A) extends Signal[A] {
  //since Events don't cache their events, but Signals do, we have to trigger this event
  //even if it isn't listened to currently
  override def becomeOrphan(): Unit = {}

  addParent(a, None)

  override protected def recalculate(recursively: Option[TriggerUpdate]): A = {
    recursively match {
      case None => init
      case Some(t) => t.getEvent(a).getOrElse(throw new SelfRxException("event is not trigger, but it should have been"))
    }
  }

  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {}
}

class EventFromSignal[A](s: Signal[A]) extends Event[A] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(s, currentTrigger)
  }

  override def recalculateRecursively(strategy: TriggerUpdate): Unit = {
    strategy.addEvent(this, s.now)
    recalculateChildren(strategy)
  }
}

class EventInsideSignal[A](s: Signal[Event[A]]) extends Event[A] {
  override def getFirstChild(currentTrigger: Option[TriggerUpdate]): Unit = {
    addParent(s, currentTrigger)
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

object Helpers {
  def mappedSignal[Z](x: Signal[_ <: Z]): selfrx.Signal[Z] = {
    new MappedSignal(x, identity[Z])
  }

  def mappedEvent[Z](x: Event[ _ <: Z]): selfrx.Event[Z] = {
    new MappedEvent(x, identity[Z])
  }
}

trait SelfRxImpl extends ReactiveLibrary with ReactiveLibraryImplementationHelper with SelfRxLoggingHelper {
  self =>
  override type Event[+A] = reactive.selfrx.Event[_ <: A]
  override type Signal[+A] = reactive.selfrx.Signal[_ <: A]
  override type Var[A] = reactive.selfrx.Variable[A]
  override type EventSource[A] = reactive.selfrx.EventSource[A]

  override def toSignal[A](init: A, event: Event[A]): Signal[A] = {
    new SignalFromEvent(new MappedEvent(event, identity[A]), init)
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
      new MergeEvent[A](new MappedEvent(x1, identity[A]), new MappedEvent(x2, identity[A]))

    override def map[A, B](fa: Event[A])(f: (A) => B): selfrx.Event[B] =
      new MappedEvent(fa, f)

    override def filter[A](v: Event[A], cond: (A) => Boolean): Event[A] =
      new FilterEvent(v, cond)
  }

  override implicit object Var extends VarCompanionObject[Var] {
    override def apply[A](init: A): Variable[A] = {
      val result = new Variable(init)
      logger.createPrimitive(result)
      result
    }
  }

  object unsafeImplicits extends UnsafeImplicits {
    override implicit object eventApplicative extends FlatMap[Event] with EventOperationsTrait[Event] {
      override def flatMap[A, B](fa: Event[A])(f: (A) => Event[B]): Event[B] = {
        val result = signalApplicative.map(toSignal(None, eventApplicative.map(fa)(Some(_)))) {
          _.map(f).getOrElse(Never)
        }

        new EventInsideSignal(new MappedSignal[Event[B], selfrx.Event[B]](Helpers.mappedSignal(result), Helpers.mappedEvent[B]))
      }

      override def map[A, B](fa: selfrx.Event[_ <: A])(f: (A) => B): selfrx.Event[_ <: B] = {
        self.eventApplicative.map(fa)(f)
      }

      override def filter[A](v: selfrx.Event[_ <: A], cond: (A) => Boolean): selfrx.Event[_ <: A] =
        self.eventApplicative.filter(v, cond)

      override def merge[A](x1: selfrx.Event[_ <: A], x2: selfrx.Event[_ <: A]): selfrx.Event[_ <: A] =
        self.eventApplicative.merge(x1, x2)
    }
    override implicit val signalApplicative: FlatMap[Signal] with SignalOperationsTrait[Signal] = self.signalApplicative
  }

  override object EventSource extends EventSourceCompanionObject[Event, EventSource] {
    override def apply[A](): EventSource[A] = {
      val result = new selfrx.EventSource[A]()
      logger.createPrimitive(result)
      result
    }
  }

  override implicit object signalApplicative extends FlatMap[Signal] with SignalOperationsTrait[Signal] {
    override def pure[A](x: A): selfrx.Signal[A] = new ConstSignal(x)

    override def ap[A, B](ff: Signal[(A) => B])(fa: Signal[A]): Signal[B] =
      new MappedSignal(new ProductSignal(ff, fa), { (v: (A => B, A)) => v._1(v._2) })


    override def map[A, B](fa: selfrx.Signal[_ <: A])(f: (A) => B): selfrx.Signal[_ <: B] =
      new MappedSignal(fa, f)

    def flatMap[A, B](fa: Signal[A])(f: (A) => Signal[B]): Signal[B] = {
      new BindSignal(new MappedSignal(fa, (x: A) => {
        new MappedSignal(f(x), identity[B]): selfrx.Signal[B]
      }))
    }

    override def flatten[A](ffa: selfrx.Signal[_ <: selfrx.Signal[_ <: A]]): selfrx.Signal[_ <: A] = {
      new BindSignal(Helpers.mappedSignal[selfrx.Signal[A]](signalApplicative.map(ffa)(Helpers.mappedSignal[A])))
    }

    //make sure we don't use flatMap if not absolutely necessary
    override def product[A, B](fa: Signal[A], fb: Signal[B]): Signal[(A, B)] =
      super[SignalOperationsTrait].product(fa, fb)
  }

  override object Signal extends SignalCompanionObject[Signal] {
    override def Const[A](value: A): selfrx.Signal[A] = new ConstSignal(value)
  }

  object Event extends EventCompanionObject[Event] {
    override def Never: selfrx.Event[Nothing] = selfrx.Never
  }
}
