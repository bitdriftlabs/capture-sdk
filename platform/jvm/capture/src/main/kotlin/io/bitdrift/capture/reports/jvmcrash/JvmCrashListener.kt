// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.jvmcrash

/**
 * Interface for handling JVM crash events.
 *
 * Classes implementing this interface will be notified when a JVM crash occurs,
 * enabling them to perform custom logic such as logging or reporting the crash.
 */
interface JvmCrashListener {
    /**
     * Called when a JVM crash is detected.
     *
     * This method is invoked immediately after an uncaught exception is thrown
     * within the JVM, providing access to the affected thread and the throwable
     * that caused the crash.
     *
     * @param thread The thread in which the uncaught exception occurred.
     * @param throwable The exception or error that triggered the crash.
     */
    fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
    )
}
