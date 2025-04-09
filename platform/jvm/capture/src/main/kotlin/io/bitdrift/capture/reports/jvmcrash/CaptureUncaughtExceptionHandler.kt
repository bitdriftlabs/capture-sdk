// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.jvmcrash

import androidx.annotation.VisibleForTesting
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Concrete implementation of [io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler]
 * that will notify the specified listener when a JVM crash has occurred
 */
internal object CaptureUncaughtExceptionHandler : ICaptureUncaughtExceptionHandler {
    private var crashing = false
    private var installed: Boolean = false
    private val prevExceptionHandler: UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()
    private val crashListeners = CopyOnWriteArrayList<JvmCrashListener>()

    @VisibleForTesting
    internal var wasExceptionForward: Boolean = false

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
            crashListeners.forEach {
                it.onJvmCrash(thread, throwable)
            }
        } catch (_: Throwable) {
            // explicitly ignore any errors caused by us
        } finally {
            // forward exception to other handlers even if we fail
            prevExceptionHandler?.uncaughtException(thread, throwable)
            wasExceptionForward = true
        }
    }

    /**
     * Installs and adds the specified listener
     */
    override fun install(jvmCrashListener: JvmCrashListener) {
        crashListeners.add(jvmCrashListener)
        if (!installed) {
            Thread.setDefaultUncaughtExceptionHandler(this)
            installed = true
        }
    }

    /**
     * Uninstall the specified listener.
     *
     */
    override fun uninstall() {
        crashListeners.clear()
        Thread.setDefaultUncaughtExceptionHandler(prevExceptionHandler)
        installed = false
    }
}
