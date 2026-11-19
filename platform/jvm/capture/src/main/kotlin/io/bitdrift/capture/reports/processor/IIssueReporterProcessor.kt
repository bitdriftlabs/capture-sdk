// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import android.app.ApplicationExitInfo
import android.os.Build
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi

/**
 * Process and persist issue types into disk for later processing in the shared-core layer.
 */
interface IIssueReporterProcessor {
    /**
     * Processes an app exit report for supported fatal reasons such as ANRs and native crashes.
     *
     * @param applicationExit The Android framework exit record to convert into an issue report.
     */
    fun processAppExitReport(applicationExit: ApplicationExitInfo)

    /**
     * Process JVM crashes into a packed format
     *
     * NOTE: This will need to run by default on the caller thread
     */
    fun processJvmCrash(
        callerThread: Thread,
        throwable: Throwable,
        allThreads: Map<Thread, Array<StackTraceElement>>?,
    )

    /**
     * Processes and persists a JavaScript report.
     *  @param errorName The main readable error name
     *  @param message The detailed JavaScript error message
     *  @param stack Raw stacktrace
     *  @param isFatalIssue Indicates if this is a fatal JSError issue
     *  @param engine Engine type (e.g. hermes/JSC)
     * @param debugId Debug id that will be used for de-minification
     * @param sdkVersion bitdrift's React Native SDK version(e.g 8.1)
     */
    fun processJavaScriptReport(
        errorName: String,
        message: String,
        stack: String,
        isFatalIssue: Boolean,
        engine: String,
        debugId: String,
        sdkVersion: String,
    )

    /**
     * Process StrictMode violations into a packed format
     *
     * NOTE: This will need to run by default on the caller thread
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun processStrictModeViolation(violation: Violation)
}
