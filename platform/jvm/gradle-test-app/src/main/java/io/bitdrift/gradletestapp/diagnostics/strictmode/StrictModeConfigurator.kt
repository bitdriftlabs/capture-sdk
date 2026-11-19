// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.gradletestapp.diagnostics.strictmode

import android.os.Build
import android.os.StrictMode
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.strictmode.CaptureStrictModeViolationListener
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object StrictModeConfigurator {
    @OptIn(ExperimentalBitdriftApi::class)
    fun install() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Timber.i("StrictMode not installed due to current SDK level ${Build.VERSION.SDK_INT}")
            return
        }
        val executor: Executor = Executors.newSingleThreadExecutor()
        val captureStrictModeViolationListener = CaptureStrictModeViolationListener()

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .penaltyListener(executor, captureStrictModeViolationListener)
                .build(),
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .penaltyListener(executor, captureStrictModeViolationListener)
                .build(),
        )
    }
}
