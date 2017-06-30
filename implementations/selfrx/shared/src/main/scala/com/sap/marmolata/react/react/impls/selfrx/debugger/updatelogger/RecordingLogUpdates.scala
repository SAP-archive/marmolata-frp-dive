package com.sap.marmolata.react.react.impls.selfrx.debugger.updatelogger

import java.util.Date

import com.sap.marmolata.react.api.ReactiveLibrary
import com.sap.marmolata.react.react.impls.selfrx._

final class OurRecordingSliceBuilder(val parent: Option[OurRecordingSliceBuilder]) extends RecordingSliceBuilder {
  var children: Seq[OurRecordingSliceBuilder] = Seq.empty
  val time: Date = new Date()
  val stackTrace: Seq[StackTraceElement] = new RuntimeException().getStackTrace.toSeq

  if (parent.isDefined) {
    val stackTrace = new RuntimeException().getStackTrace
    //TDDO: use console.log when in js, some other logging when in jvm
    println(s"ReactiveLibrary: doing an update inside an update. Instead, use other combinators $stackTrace")
  }

  var updatedValues: Seq[Primitive] = Seq.empty

  override def addPrimitiveChange[A](p: RecordForPlayback[A], before: A, after: A): Unit = {}
  override def currentRecordingMode: RecordingMode = RecordingMode.Record

  override def aboutToRecalculate(p: Primitive): Unit = {
    updatedValues +:= p
  }
}

class RecordingLogUpdates extends Recording {
  var recordingHistory: Seq[OurRecordingSliceBuilder] = Seq.empty
  var currentRecordingSlice: Option[OurRecordingSliceBuilder] = None

  override def startNewRecording(): RecordingSliceBuilder = {
    val result = new OurRecordingSliceBuilder(currentRecordingSlice)
    currentRecordingSlice = Some(result)
    result
  }

  override def finishCurrentRecording(recording: RecordingSliceBuilder): Unit = {
    if (currentRecordingSlice.contains(recording)) {
      val rr = recording.asInstanceOf[OurRecordingSliceBuilder]
      currentRecordingSlice = rr.parent
      currentRecordingSlice match {
        case None => recordingHistory :+= rr
        case Some(t) => t.children :+= rr
      }
    }
    else {
      throw new Exception("ReactiveLibrary: internal error in record logging. If this happens, please file a bug report")
    }
  }
}

trait HasHistoryLogger {
  self: ReactiveLibrary =>
  val historyLogger: RecordingLogUpdates
}

class LogSelfrxImpl extends SelfRxImpl with HasHistoryLogger {
  val historyLogger: RecordingLogUpdates = new RecordingLogUpdates()

  override implicit def recording: RecordingLogUpdates = historyLogger
}
