package react.debug

import react.ReactiveLibrary.Nameable

trait AnnotateStack extends DebugLayer with EnsureLargeEnoughStackTrace {
  def checkStackframe(s: StackTraceElement): Option[String] = Some(s.toString)

  override def onNew(u: HasUnderlying[Nameable]): Unit = {
    super.onNew(u)
    val currentStackTrace = new RuntimeException().getStackTrace
    val stackframe = currentStackTrace.collectFirst {
      case st if List("cats",
        "react",
        "reactive",
        "java",
        "<jscode>",
        "scala"
      ).forall(!st.getClassName.startsWith(_)) && checkStackframe(st).isDefined =>
        checkStackframe(st).get
    }.getOrElse("empty stack trace - your browser may not be supported")
    u.under.name += stackframe
  }

  override def implementationName: String = s"annotate-stack-of-${super.implementationName}"
}