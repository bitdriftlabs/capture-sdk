// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.gradletestapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.LoggerImpl
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


/**
 * Artificially creates different types of Fatal issues (ANR, JVM Crash, Native Crash,etc)
 */
internal object FatalIssueGenerator {

    private val uuidSubject: BehaviorSubject<String> = BehaviorSubject.create();
    private val oomList = mutableListOf<ByteArray>()
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    fun forceDeadlockAnr() {
        callOnMainThread {
            initializeInBackground()
            startProcessing()
        }
    }

    fun forceThreadSleepAnr() {
        callOnMainThread {
            Thread.sleep(SLEEP_DURATION_MILLI)
        }
    }

    fun forceBlockingGetAnr() {
        callOnMainThread {
            val aResultWillNeverGet = uuidSubject.blockingFirst()
            Log.e(TAG_NAME, aResultWillNeverGet)
        }
    }

    fun forceBroadcastReceiverAnr(context: Context) {
        callOnMainThread {
            val filter = IntentFilter(TRIGGER_BROADCAST_RECEIVER_ANR)
            ContextCompat.registerReceiver(
                context,
                AnrBroadcastReceiver(),
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            val intent = Intent(TRIGGER_BROADCAST_RECEIVER_ANR).setPackage(context.packageName)
            context.sendBroadcast(intent)
        }
    }

    fun forceUnhandledException() {
        throw RuntimeException("Forced unhandled exception")
    }

    @SuppressLint("CheckResult")
    fun forceRxJavaException() {
        Observable.error<String>(Throwable("Artificial exception"))
            .subscribe { item -> Log.i(TAG_NAME, "Item received: $item") }
            // Missing error explicitly in order to crash

    }

    fun forceCoroutinesAnr() {
        CoroutineScope(Dispatchers.Main).launch {
            (1..Int.MAX_VALUE).asFlow()
                .onEach {
                    Thread.sleep(1)
                }
                .collect {
                    Log.i(TAG_NAME, "Item received: $it")
                }
        }
    }


    fun forceCoroutinesCrash(){
        CoroutineScope(Dispatchers.IO).launch {
            throw RuntimeException("Coroutine background thread crash")
        }
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

    private fun callOnMainThread(action: () -> Unit) {
        mainThreadHandler.post(action)
    }

    private val FIRST_LOCK_RESOURCE: Any = "first_lock"
    private val SECOND_LOCK_RESOURCE: Any = "second_lock"
    private val TAG_NAME = "FatalIssueGenerator"
    private const val THREAD_DELAY_IN_MILLI: Long = 10
    private const val SLEEP_DURATION_MILLI = 15000L
    private const val TRIGGER_BROADCAST_RECEIVER_ANR =
        "io.bitdrift.gradletestapp.broadcastreceiver.ANR_TRIGGER"
    private fun logThreadStatus(lockInfo: String) {
        Log.d("DEADLOCK_TAG", "Thread [" + Thread.currentThread().name + "] " + lockInfo)
    }

    private class AnrBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (TRIGGER_BROADCAST_RECEIVER_ANR == intent?.action) {
                repeat(1000) {
                    val result = fetchResult(it)
                    Log.i("AnrBroadcastReceiver", result)
                }
            }
        }

        /**
         * "Simulates" an expensive IO operation
         */
        private fun fetchResult(id: Int): String {
            val startTime = System.currentTimeMillis()
            var result = 0L
            for (i in 1..1_000_000_000) {
                result += i
            }
            val endTime = System.currentTimeMillis()
            val duration = (endTime - startTime)
            return "Task ID [$id] completed with a $duration of milliseconds"
        }
    }
}