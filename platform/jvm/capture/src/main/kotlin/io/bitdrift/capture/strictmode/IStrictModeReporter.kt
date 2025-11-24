// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.strictmode

import android.os.Build
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import io.bitdrift.capture.Capture

/**
 * Interface for reporting StrictMode violations to Capture.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal interface IStrictModeReporter {
    fun report(violation: Violation)
}

@RequiresApi(Build.VERSION_CODES.P)
internal object StrictModeReporter : IStrictModeReporter {
    override fun report(violation: Violation) {
        Capture.Logger.reportStrictModeViolation(violation)
    }
}
