// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.strictmode

import android.os.Build
import android.os.StrictMode
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object StrictModeConfigurator {
    fun install() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Timber.i("StrictMode not installed due to current SDK level ${Build.VERSION.SDK_INT}")
            return
        }
        val reporter = StrictModeReporter()
        val executor: Executor = Executors.newSingleThreadExecutor()

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .penaltyListener(executor) {
                    reporter.onViolation(StrictModeReporter.ViolationType.THREAD, it)
                }.build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .penaltyListener(executor) {
                    reporter.onViolation(StrictModeReporter.ViolationType.VM, it)
                }.build(),
        )
    }
}
