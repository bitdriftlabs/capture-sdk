// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.os.Build
import android.os.strictmode.Violation
import android.util.Log
import androidx.annotation.RequiresApi
import io.bitdrift.capture.Capture

/**
 * Reports Strict mode violations
 */
@RequiresApi(Build.VERSION_CODES.P)
internal class StrictModeReporter {

    fun onViolation(violationType: ViolationType, violation: Violation) {
        val cause = violation.toString()
        val stackTrace = Log.getStackTraceString(violation)
        val strictModeFields = mapOf(
            "strict_mode_reason" to violation.toString(),
            "strict_mode_stack_trace" to stackTrace
        )
        val details = "StrictMode ${violationType.name} violation. " + cause
        Capture.Logger.logError(strictModeFields) { details }
    }

    internal enum class ViolationType {
        VM,
        THREAD
    }
}