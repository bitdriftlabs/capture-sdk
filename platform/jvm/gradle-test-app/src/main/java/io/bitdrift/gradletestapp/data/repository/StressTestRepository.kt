// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.repository

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StrictMode
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import io.bitdrift.capture.Capture
import io.bitdrift.gradletestapp.diagnostics.fatalissues.FatalIssueGenerator
import io.bitdrift.gradletestapp.data.model.StrictModeViolationType
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StressTestRepository(
    private val context: Context,
) {
    private val oomList = mutableListOf<ByteArray>()
    private var memoryPressureThread: Thread? = null

    fun increaseMemoryPressure(targetPercent: Int) {
        Capture.Logger.logWarning {
            "Started memory pressure trigger with $targetPercent% target"
        }
        stopAllThreads()
        oomList.clear()

        val targetUsage = (maxMemory() * targetPercent / 100.0).toLong()
        memoryPressureThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    oomList.add(ByteArray(1024 * 1024))
                    Thread.sleep(250)
                    if (usedMemory() >= targetUsage) {
                        Capture.Logger.logError {
                            "Increased memory pressure to $targetPercent%"
                        }
                        break
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.also { it.start() }
    }

    fun triggerMemoryPressureAnr() {
        stopAllThreads()
        oomList.clear()

        Thread {
            val targetUsage = (maxMemory() * 0.98).toLong()
            while (usedMemory() < targetUsage) {
                try {
                    oomList.add(ByteArray(1024 * 1024))
                } catch (e: OutOfMemoryError) {
                    break
                }
            }

            Capture.Logger.logWarning { "Memory filled to 98%, starting main thread allocations" }
            Handler(Looper.getMainLooper()).post {
                val startTime = System.currentTimeMillis()
                val tempList = mutableListOf<ByteArray>()

                while (System.currentTimeMillis() - startTime < 10000) {
                    try {
                        tempList.add(ByteArray(512 * 1024))
                        if (tempList.size > 20) {
                            tempList.clear()
                        }
                    } catch (_: OutOfMemoryError) {
                        tempList.clear()
                    }
                }
                Capture.Logger.logError { "ANR test completed after ${System.currentTimeMillis() - startTime}ms" }
            }
        }.start()
    }

    fun createThreads(count: Int) {
        require(count > 0) { "Thread count must be positive" }
        Capture.Logger.logWarning(mapOf("thread_count" to count.toString())) {
            "Started creating $count sleeping threads"
        }
        FatalIssueGenerator.forceThreadCount(count)
    }

    private fun stopAllThreads() {
        memoryPressureThread?.interrupt()
        memoryPressureThread = null
    }

    fun triggerJankyFrames(durationMs: Long) {
        Handler(Looper.getMainLooper()).post {
            Thread.sleep(durationMs)
            Timber.d("Jank sleep done: ${durationMs}ms")
        }
    }

    fun triggerStrictModeViolation(type: StrictModeViolationType) {
        Handler(Looper.getMainLooper()).post {
            when (type) {
                StrictModeViolationType.DiskRead -> triggerDiskReadViolation()
                StrictModeViolationType.DiskWrite -> triggerDiskWriteViolation()
                StrictModeViolationType.Network -> triggerNetworkViolation()
                StrictModeViolationType.CustomSlowCall -> triggerCustomSlowCallViolation()
                StrictModeViolationType.UntaggedSocket -> triggerUntaggedSocketViolation()
            }
        }
    }

    private fun triggerDiskReadViolation() {
        try {
            val file = File("/proc/version")
            FileInputStream(file).use { it.read() }
        } catch (e: Exception) {
            Timber.d("Expected exception during StrictMode test: ${e.message}")
        }
    }

    private fun triggerDiskWriteViolation() {
        try {
            File(context.cacheDir, "strictmode-write.txt").outputStream().use {
                it.write("strict-mode-write".toByteArray())
            }
        } catch (e: Exception) {
            Timber.d("Expected exception during StrictMode test: ${e.message}")
        }
    }

    /**
     * Before fix for BIT-8521, it reproduces the ConcurrentModificationException in ReplayDecorations.addDecorations on
     * API < 29 by racing screen replay captures against rapid main-thread mutations of
     * WindowManagerGlobal.mViews (dialogs, popups, toasts, and an injected trigger view).
     */
    fun triggerScreenReplayCapture(activity: Activity) {
        val deadline = System.currentTimeMillis() + REPLAY_RACE_DURATION_MS
        val mainHandler = Handler(Looper.getMainLooper())
        val activityToken = activity.window.decorView.windowToken
        val triggerView = RootMutationTriggerView(activity, mainHandler, activityToken)

        triggerView.attachTo(activity.windowManager)
        repeat(CONCURRENT_CHURN_RUNNERS) {
            mainHandler.post(WindowChurnRunnable(activity, mainHandler, deadline, triggerView))
        }
        startReplayCaptureDriver(deadline)
    }

    private fun startReplayCaptureDriver(deadline: Long) {
        Thread({
            while (System.currentTimeMillis() < deadline) {
                Capture.Logger.logScreenView("Stress ${System.nanoTime()}")
                Thread.sleep(REPLAY_CAPTURE_INTERVAL_MS)
            }
        }, "replay-race-driver").start()
    }

    /**
     * Repeatedly creates and dismisses dialogs/popups/toasts on the main thread, growing the
     * set of attached windows seen by WindowManagerGlobal until the deadline is reached.
     */
    private class WindowChurnRunnable(
        private val activity: Activity,
        private val mainHandler: Handler,
        private val deadline: Long,
        private val triggerView: RootMutationTriggerView,
    ) : Runnable {
        private val dialogs = ArrayDeque<Dialog>()
        private val popups = ArrayDeque<PopupWindow>()
        private var counter = 0

        override fun run() {
            repeat(CHURN_STEPS_PER_TICK) {
                dialogs.removeFirstOrNull()?.dismiss()
                popups.removeFirstOrNull()?.dismiss()

                val size = MIN_WINDOW_SIZE_PX + (counter % WINDOW_SIZE_RANGE_PX)
                dialogs.addLast(createStressDialog(activity, counter, size))
                popups.addLast(createStressPopup(activity, counter, size))
                counter++
            }

            Toast.makeText(activity, "stress $counter", Toast.LENGTH_SHORT).show()

            if (System.currentTimeMillis() < deadline) {
                mainHandler.postDelayed(this, CHURN_INTERVAL_MS)
            } else {
                dialogs.forEach { it.dismiss() }
                popups.forEach { it.dismiss() }
                triggerView.detach()
            }
        }
    }

    /**
     * A no-op overlay View whose getRootWindowInsets() forces a synchronous main-thread
     * addView/removeView when called off the main thread, mutating WindowManagerGlobal.mViews
     * exactly while ReplayDecorations is iterating it on the background executor.
     */
    private class RootMutationTriggerView(
        context: Context,
        private val mainHandler: Handler,
        private val activityToken: IBinder,
    ) : View(context) {
        private var windowManager: WindowManager? = null

        fun attachTo(windowManager: WindowManager) {
            runCatching {
                windowManager.addView(this, overlayLayoutParams(activityToken))
                this.windowManager = windowManager
            }
        }

        fun detach() {
            val wm = windowManager ?: return
            windowManager = null
            removeViewIfAttached(wm, this)
        }

        override fun getRootWindowInsets(): WindowInsets? {
            val wm = windowManager
            if (wm == null || Looper.myLooper() == Looper.getMainLooper()) {
                return super.getRootWindowInsets()
            }
            mutateWindowsOnMainThread(wm)
            return super.getRootWindowInsets()
        }

        private fun mutateWindowsOnMainThread(wm: WindowManager) {
            val latch = CountDownLatch(1)
            mainHandler.post {
                runCatching {
                    val transient = View(context)
                    wm.addView(transient, overlayLayoutParams(activityToken))
                    removeViewIfAttached(wm, transient)
                }
                latch.countDown()
            }
            latch.await(MAIN_THREAD_MUTATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    companion object {
        private const val REPLAY_RACE_DURATION_MS = 10_000L
        private const val REPLAY_CAPTURE_INTERVAL_MS = 20L
        private const val CHURN_INTERVAL_MS = 16L
        private const val CHURN_STEPS_PER_TICK = 2
        private const val CONCURRENT_CHURN_RUNNERS = 12
        private const val MAIN_THREAD_MUTATION_TIMEOUT_MS = 200L
        private const val MIN_WINDOW_SIZE_PX = 80
        private const val WINDOW_SIZE_RANGE_PX = 160

        private fun overlayLayoutParams(token: IBinder): WindowManager.LayoutParams =
            WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply { this.token = token }

        private fun removeViewIfAttached(windowManager: WindowManager, view: View) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            }
        }

        private fun createStressDialog(activity: Activity, counter: Int, size: Int): Dialog =
            Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(
                    TextView(activity).apply {
                        text = "Dialog $counter"
                        minimumWidth = size
                        minimumHeight = size
                    },
                )
                window?.setDimAmount(0f)
                show()
                window?.setLayout(size, size)
            }

        private fun createStressPopup(activity: Activity, counter: Int, size: Int): PopupWindow =
            PopupWindow(
                TextView(activity).apply {
                    text = "Popup $counter"
                    minimumWidth = size
                    minimumHeight = size
                },
                size,
                size,
                false,
            ).apply {
                isClippingEnabled = false
                showAtLocation(
                    activity.window.decorView,
                    Gravity.TOP or Gravity.START,
                    counter % 200,
                    counter % 300,
                )
            }
    }

    private fun triggerNetworkViolation() {
        try {
            URL("https://10.0.2.2:1").openConnection().getInputStream().use { it.read() }
        } catch (e: Exception) {
            Timber.d("Expected exception during StrictMode test: ${e.message}")
        }
    }

    private fun triggerCustomSlowCallViolation() {
        StrictMode.noteSlowCall("Triggered custom StrictMode slow call")
    }

    private fun triggerUntaggedSocketViolation() {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("10.0.2.2", 1), 250)
            }
        } catch (e: Exception) {
            Timber.d("Expected exception during StrictMode test: ${e.message}")
        }
    }

    private fun maxMemory(): Long {
        return Runtime.getRuntime().maxMemory()
    }

    private fun usedMemory(): Long {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    }
}
