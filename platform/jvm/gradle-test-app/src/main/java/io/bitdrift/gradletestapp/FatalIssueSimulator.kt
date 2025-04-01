// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.gradletestapp

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.LoggerImpl
import io.reactivex.rxjava3.subjects.BehaviorSubject

/**
 * Artificially creates different types of Fatal issues (ANR, JVM Crash, Native Crash,etc)
 */
internal object FatalIssueSimulator {

    private val uuidSubject: BehaviorSubject<String> = BehaviorSubject.create();
    private val oomList = mutableListOf<ByteArray>()

    fun forceDeadlockAnr() {
        initializeInBackground()
        startProcessing()
    }

    fun forceThreadSleepAnr() {
        Handler(Looper.getMainLooper()).post {
            Thread.sleep(15000)
        }
    }

    fun forceBlockingGetAnr() {
        val aResultWillNeverGet = uuidSubject.blockingFirst()
        Log.e("FatalIssueSimulator", aResultWillNeverGet)
    }

    fun forceUnhandledException() {
        throw RuntimeException("Forced unhandled exception")
    }

    fun forceNativeCrash() {
        val logger = Capture.logger()
        CaptureJniLibrary.destroyLogger((logger as LoggerImpl).loggerId)
        Logger.logInfo { "Forced native crash" }
    }

    fun forceOutOfMemoryCrash() {
        if (oomList.isNotEmpty()) {
            return
        }
        Thread {
            while (true) {
                oomList.add(ByteArray(1024 * 1024))
                Thread.sleep(50)
            }
        }.start()
    }

    private fun initializeInBackground() {
        val backgroundThread: Thread = object : Thread() {
            override fun run() {
                synchronized(SECOND_LOCK_RESOURCE) {
                    logThreadStatus("waiting on second lock")
                    try {
                        sleep(THREAD_DELAY_IN_MILLI)
                    } catch (_: InterruptedException) {
                    }
                    synchronized(FIRST_LOCK_RESOURCE) { logThreadStatus("waiting on first lock") }
                }
            }
        }
        backgroundThread.name = "background_thread_for_deadlock_demo"
        backgroundThread.start()
    }

    private fun startProcessing() {
        synchronized(FIRST_LOCK_RESOURCE) {
            logThreadStatus("waiting on first lock")
            try {
                Thread.sleep(THREAD_DELAY_IN_MILLI)
            } catch (_: InterruptedException) {
            }
            synchronized(SECOND_LOCK_RESOURCE) { logThreadStatus("waiting on second lock") }
        }
    }

    private val FIRST_LOCK_RESOURCE: Any = "first_lock"
    private val SECOND_LOCK_RESOURCE: Any = "second_lock"
    private const val THREAD_DELAY_IN_MILLI: Long = 10
    private fun logThreadStatus(lockInfo: String) {
        Log.d("DEADLOCK_TAG", "Thread [" + Thread.currentThread().name + "] " + lockInfo)
    }
}