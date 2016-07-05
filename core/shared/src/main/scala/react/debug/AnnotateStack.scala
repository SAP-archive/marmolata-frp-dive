package react.debug

import react.ReactiveLibrary.Nameable

import scala.collection.mutable

trait AnnotateStack extends DebugLayer with EnsureLargeEnoughStackTrace {
  def checkStackframe(s: StackTraceElement): Boolean = true

  def filterPackages = List("cats", "react", "reactive", "java", "scala", "<jscode>")


  //TODO use js WeakMap
  var traces: mutable.HashMap[Nameable, Seq[StackTraceElement]] = mutable.HashMap.empty

  def stackFrom(t: Nameable): Seq[StackTraceElement] = {
    traces.getOrElse(t, Seq.empty)
  }

  override def onNew(u: HasUnderlying[Nameable]): Unit = {
    super.onNew(u)
    val currentStackTrace = new RuntimeException().getStackTrace
    val stackframes = currentStackTrace.collect {
      case st if filterPackages.forall(!st.getClassName.startsWith(_)) && checkStackframe(st) => st
    }.toSeq
    traces += ((u.under, stackframes))
  }

  override def implementationName: String = s"annotate-stack-of-${super.implementationName}"
}