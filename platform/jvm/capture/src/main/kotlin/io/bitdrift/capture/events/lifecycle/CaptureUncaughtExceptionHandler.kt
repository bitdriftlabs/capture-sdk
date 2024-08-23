// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import java.lang.Thread.UncaughtExceptionHandler

internal class CaptureUncaughtExceptionHandler(
    private val prevExceptionHandler: UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler(),
) : UncaughtExceptionHandler {
    private lateinit var crashReporter: AppExitLogger
    private var crashing = false

    fun install(crashReporter: AppExitLogger) {
        this.crashReporter = crashReporter
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun uninstall() {
        Thread.setDefaultUncaughtExceptionHandler(prevExceptionHandler)
    }
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // avoid re-entry
        if (crashing) {
            return
        }

        try {
            crashing = true
            crashReporter.logCrash(thread, throwable)
        } catch (_: Throwable) {
            // explicitly ignore any errors caused by us
        } finally {
            // forward exception to other handlers even if we fail
            prevExceptionHandler?.uncaughtException(thread, throwable)
        }
    }
}
