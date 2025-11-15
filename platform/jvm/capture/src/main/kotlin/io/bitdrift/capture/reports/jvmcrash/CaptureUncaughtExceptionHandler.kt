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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Concrete implementation of [io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler]
 * that will notify the specified listener when a JVM crash has occurred.
 */
object CaptureUncaughtExceptionHandler : ICaptureUncaughtExceptionHandler {
    /**
     * TBF oh yeah
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val crashing = AtomicBoolean(false)
    private val installed = AtomicBoolean(false)
    private var prevExceptionHandler: UncaughtExceptionHandler? = null
    private val crashListeners = CopyOnWriteArrayList<IJvmCrashListener>()

    /**
     * TBF
     */
    fun createNonFatal() {
        crashListeners.forEach {
            it.onJvmCrash(Thread(), IllegalStateException(), true)
        }
    }

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            val shouldNotifyListeners = crashing.compareAndSet(false, true)
            if (shouldNotifyListeners) {
                crashListeners.forEach {
                    it.onJvmCrash(thread, throwable, false)
                }
            }
        } catch (_: Throwable) {
            // explicitly ignore any errors caused by us
        } finally {
            // forward exception to other handlers even if we fail
            prevExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Installs and adds the specified listener
     */
    override fun install(crashListener: IJvmCrashListener) {
        crashListeners.add(crashListener)
        if (installed.compareAndSet(false, true)) {
            prevExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    /**
     * Uninstall the specified listener.
     *
     */
    override fun uninstall() {
        crashListeners.clear()
        Thread.setDefaultUncaughtExceptionHandler(prevExceptionHandler)
        installed.set(false)
    }
}
