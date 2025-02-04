package io.bitdrift.capture.threading

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Different Dispatchers used by Capture.
 *
 * The initial implementation exposes an [executorService]
 */
sealed class CaptureDispatchers private constructor(
    threadName: String,
) {
    /**
     * Returns the associated [ExecutorService] for the [CaptureDispatchers] type
     */
    val executorService: ExecutorService =
        buildExecutorService(threadName)
            .also { register(this) }

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
        fun shutdownAll() {
            initializedDispatchers.forEach { it.executorService.shutdown() }
            initializedDispatchers.clear()
        }

        private fun register(dispatcher: CaptureDispatchers) {
            initializedDispatchers.add(dispatcher)
        }
    }
}
