// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.strictmode

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.threading.CaptureDispatchers

/**
 * A listener for StrictMode violations that reports them to Capture.
 * This listener handles both ThreadPolicy and VmPolicy violations.
 *
 * Usage:
 * ```
 * val listener = CaptureStrictModeViolationListener()
 * StrictMode.setThreadPolicy(
 *     StrictMode.ThreadPolicy.Builder()
 *         .detectAll()
 *         .penaltyListener(executor, listener)
 *         .build()
 * )
 * StrictMode.setVmPolicy(
 *     StrictMode.VmPolicy.Builder()
 *         .detectAll()
 *         .penaltyListener(executor, listener)
 *         .build()
 * )
 * ```
 *
 * NOTE: This is still a new experimental API and may suffer some changes.
 * When we are ready for it will open visibility to customers (please note that the
 * constructor should remain internal)
 *
 */
@ExperimentalBitdriftApi()
@RequiresApi(Build.VERSION_CODES.P)
internal class CaptureStrictModeViolationListener internal constructor(
    private val backgroundThreadHandler: IBackgroundThreadHandler,
    private val strictModeReporter: IStrictModeReporter,
) : StrictMode.OnThreadViolationListener,
    StrictMode.OnVmViolationListener {
    constructor() : this(
        CaptureDispatchers.CommonBackground,
        StrictModeReporter,
    )

    override fun onThreadViolation(violation: Violation) {
        processViolation(violation)
    }

    override fun onVmViolation(violation: Violation) {
        processViolation(violation)
    }

    private fun processViolation(violation: Violation) {
        backgroundThreadHandler.runAsync {
            strictModeReporter.report(violation)
        }
    }
}
