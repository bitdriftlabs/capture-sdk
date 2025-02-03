package io.bitdrift.capture.threading

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Different Dispatchers used by Capture.
 *
 * The initial implementation exposes an [executorService]
 */
sealed class CaptureDispatcher private constructor(private val threadName: String) {

    /**
     * Returns the associated [ExecutorService] for the [CaptureDispatcher] type
     */
    val executorService: ExecutorService = buildExecutorService(threadName)
            .also { register(this) }

    /**
     * [ExecutorService] to be used for handling different types of [EventsListenerTarget]
     */
    object EventListener : CaptureDispatcher("event-listener")

    /**
     * [ExecutorService] to be used for networking capture
     */
    object Network: CaptureDispatcher("network.okhttp")

    /**
     * [ExecutorService] to be used for Session-Replay
     */
    object SessionReplay: CaptureDispatcher("session-replay")

    private fun buildExecutorService(threadName: String): ExecutorService {
        return Executors.newSingleThreadExecutor {
            Thread(it, "$CAPTURE_EXECUTOR_SERVICE_NAME.$threadName")
        }
    }

    @Suppress("UndocumentedPublicClass")
    companion object {
        private const val CAPTURE_EXECUTOR_SERVICE_NAME = "io.bitdrift.capture"

        private val initializedDispatchers = mutableListOf<CaptureDispatcher>()

        /**
         * Shuts down all used dispatchers
         */
        fun shutdownAll() {
            initializedDispatchers.forEach { captureDispatcher ->
                captureDispatcher.executorService.shutdown()
            }
            initializedDispatchers.clear()
        }

        private fun register(dispatcher: CaptureDispatcher) {
            if (!initializedDispatchers.contains(dispatcher)) {
                initializedDispatchers.add(dispatcher)
            }
        }
    }
}
