// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.jvmcrash

/**
 * [java.lang.Thread.UncaughtExceptionHandler] that will notify crashes into the added [io.bitdrift.capture.reports.jvmcrash.JvmCrashListener]
 */
interface ICaptureUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
    /**
     * Installs the [java.lang.Thread.UncaughtExceptionHandler] and notifies [io.bitdrift.capture.reports.jvmcrash.JvmCrashListener] when
     * a JVM crash occurs
     */
    fun install(jvmCrashListener: JvmCrashListener)

    /**
     * Uninstalls [java.lang.Thread.UncaughtExceptionHandler]
     */
    fun uninstall()
}
