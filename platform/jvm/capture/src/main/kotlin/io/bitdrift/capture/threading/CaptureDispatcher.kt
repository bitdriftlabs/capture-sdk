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
    val executorService: ExecutorService by lazy {
        buildExecutorService(threadName)
    }

    /**
     * [ExecutorService] to be used for processing main events
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

    /**
     * Tears down the existing service
     */
    fun shutDown() {
        executorService.shutdown()
    }

    private fun buildExecutorService(threadName: String): ExecutorService {
        return Executors.newSingleThreadExecutor {
            Thread(it, "$CAPTURE_EXECUTOR_SERVICE_NAME.$threadName")
        }
    }

    private companion object {
        private const val CAPTURE_EXECUTOR_SERVICE_NAME = "io.bitdrift.capture"
    }
}