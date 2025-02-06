// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.threading

import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.common.IBackgroundThreadHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Different Dispatchers used by Capture.
 *
 * The initial implementation exposes an [executorService]
 */
internal sealed class CaptureDispatchers private constructor(
    private val threadName: String,
) : IBackgroundThreadHandler {
    private var executorService: ExecutorService =
        buildExecutorService(threadName)
            .also { register(this) }

    override fun runAction(action: () -> Unit) {
        executorService.execute(action)
    }

    override fun shutdown() {
        executorService.shutdown()
    }

    override fun threadName(): String = threadName

    override fun asExecutorService(): ExecutorService = executorService

    /**
     * [ExecutorService] to be used for handling different types of [EventsListenerTarget]
     */
    object EventListener : CaptureDispatchers("event-listener")

    /**
     * [ExecutorService] to be used for networking capture
     */
    object Network : CaptureDispatchers("network.okhttp")

    /**
     * [ExecutorService] to be used for Session-Replay
     */
    object SessionReplay : CaptureDispatchers("session-replay")

    private fun buildExecutorService(threadName: String): ExecutorService =
        Executors.newSingleThreadExecutor {
            Thread(it, "$CAPTURE_EXECUTOR_SERVICE_NAME.$threadName")
        }

    /**
     * Exposes [CapturedDispatchers.shutdownAll]
     */
    companion object {
        private const val CAPTURE_EXECUTOR_SERVICE_NAME = "io.bitdrift.capture"

        private val initializedDispatchers = mutableSetOf<CaptureDispatchers>()

        /**
         * Shuts down all used dispatchers
         */
        @JvmStatic
        fun shutdownAll() {
            initializedDispatchers.forEach { it.executorService.shutdown() }
            initializedDispatchers.clear()
        }

        /**
         * Sets a test [ExecutorService] for the specified Dispatcher
         */
        @JvmStatic
        @VisibleForTesting
        internal fun setTestExecutorService(testExecutorService: ExecutorService) {
            listOf(EventListener, Network, SessionReplay).forEach {
                it.executorService = testExecutorService
            }
        }

        private fun register(dispatcher: CaptureDispatchers) {
            initializedDispatchers.add(dispatcher)
        }
    }
}
