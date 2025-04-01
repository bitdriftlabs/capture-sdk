// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.reports.FatalIssueMechanism
import io.bitdrift.capture.reports.IFatalIssueReporter
import java.lang.Thread.UncaughtExceptionHandler

internal class CaptureUncaughtExceptionHandler(
    private val errorHandler: ErrorHandler,
    private val fatalIssueReporter: IFatalIssueReporter,
    private val prevExceptionHandler: UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler(),
) : UncaughtExceptionHandler {
    private lateinit var appExitLogger: AppExitLogger
    private var crashing = false

    fun install(appExitLogger: AppExitLogger) {
        this.appExitLogger = appExitLogger
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun uninstall() {
        Thread.setDefaultUncaughtExceptionHandler(prevExceptionHandler)
    }

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        // avoid re-entry
        if (crashing) {
            return
        }

        try {
            crashing = true
            if (FatalIssueMechanism.BUILT_IN == fatalIssueReporter.fetchStatus().mechanism) {
                fatalIssueReporter.persistJvmCrash(
                    errorHandler = errorHandler,
                    timestamp = System.currentTimeMillis(),
                    thread,
                    throwable,
                )
                return
            }
            appExitLogger.logCrash(thread, throwable)
        } catch (_: Throwable) {
            // explicitly ignore any errors caused by us
        } finally {
            // forward exception to other handlers even if we fail
            prevExceptionHandler?.uncaughtException(thread, throwable)
        }
    }
}
