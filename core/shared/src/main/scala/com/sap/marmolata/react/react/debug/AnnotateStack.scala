package com.sap.marmolata.react.react.debug

import com.sap.marmolata.react.api.ReactiveLibrary
import com.sap.marmolata.react.api.EnsureLargeEnoughStackTrace

import scala.annotation.tailrec
import scala.collection.mutable

trait AnnotateStackAnnotation extends Annotation

trait AnnotateStack extends DebugLayer with EnsureLargeEnoughStackTrace {
  def checkStackframe(s: StackTraceElement): Boolean = true

  def filterPackages = List("cats", "react", "reactive", "java", "scala", "<jscode>")


  //TODO use js WeakMap
  var traces: mutable.HashMap[Annotateable, Seq[StackTraceElement]] = mutable.HashMap.empty

  def stackFrom(t: Annotateable): Seq[StackTraceElement] = {
    traces.getOrElse(t, Seq.empty)
  }

  def annotateStackCondition(u: HasUnderlying[Annotateable]): Boolean = {
    def shouldAnnotateStack(x: Annotation): Boolean =
      x.isInstanceOf[AnnotateStackAnnotation] || x.parent.exists(shouldAnnotateStack)
    u.under.allAnnotations.exists(shouldAnnotateStack)
  }

  override def onNew(u: HasUnderlying[Annotateable]): Unit = {
    super.onNew(u)
    if (annotateStackCondition(u)) {
      val currentStackTrace = new RuntimeException().getStackTrace
      val stackframes = currentStackTrace.collect {
        case st if filterPackages.forall(!st.getClassName.startsWith(_)) && checkStackframe(st) => st
      }.toSeq
      traces += ((u.under, stackframes))
    }
  }

  override def implementationName: String = s"annotate-stack-of-${super.implementationName}"
}