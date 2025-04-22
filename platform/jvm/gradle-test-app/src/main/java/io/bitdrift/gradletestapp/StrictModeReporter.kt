// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE
import android.app.ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION
import android.app.ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME
import android.app.ApplicationStartInfo.START_TIMESTAMP_FORK
import android.app.ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN
import android.app.ApplicationStartInfo.START_TIMESTAMP_INITIAL_RENDERTHREAD_FRAME
import android.app.ApplicationStartInfo.START_TIMESTAMP_LAUNCH
import android.app.ApplicationStartInfo.START_TIMESTAMP_SURFACEFLINGER_COMPOSITION_COMPLETE
import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import android.util.Log
import androidx.annotation.RequiresApi
import io.bitdrift.capture.Capture

/**
 * Reports Strict mode violation
 */
@RequiresApi(Build.VERSION_CODES.P)
class StrictModeReporter : StrictMode.OnVmViolationListener, StrictMode.OnThreadViolationListener {

    override fun onVmViolation(violation: Violation) {
        reportViolation(Type.VM, violation)
    }

    override fun onThreadViolation(violation: Violation) {
        reportViolation(Type.THREAD, violation)
    }

    private fun reportViolation(type: Type, violation: Violation) {
        val cause = violation.toString()
        val stackTrace = Log.getStackTraceString(violation)
        val strictModeFields = mapOf(
            "strict_mode_reason" to violation.toString(),
            "strict_mode_stack_trace" to stackTrace
        )
        val details = "StrictMode ${type.name} violation. " + cause
        Capture.Logger.logError (strictModeFields) { details }
    }

    private enum class Type{
        VM,
        THREAD
    }
}