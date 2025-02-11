// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.Application
import android.util.Log
import android.widget.Toast
import io.bitdrift.capture.LoggerState
import io.bitdrift.capture.ICaptureStartListener

/**
 * A generic monitor for [Capture.Logger.start()] call
 */
class CaptureStartMonitor(private val application: Application) : ICaptureStartListener {

    override fun onLoggerStateUpdate(loggerState: LoggerState) {
        val message = when (loggerState) {
            is LoggerState.NotStarted -> "Capture SDK not started yet"
            is LoggerState.Starting -> "Capture SDK starting now"
            is LoggerState.Started -> "Capture SDK started. Took ${loggerState.startDuration} on ${loggerState.callerThreadName} thread"
            is LoggerState.StartFailure -> "Capture SDK start failure. ${loggerState.throwable.message}"
        }
        Toast.makeText(application, message, Toast.LENGTH_LONG).show()
        Log.v("GradleTestApp", message)
    }
}