package io.bitdrift.capture.utils

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureExecutors {
    private val executors: List<ExecutorService>

    val eventListener: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "io.bitdrift.capture.event-listener")
    }
    val captureNetwork: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "io.bitdrift.capture.network.okhttp")
    }
    val sessionReplay: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "io.bitdrift.capture.session-replay")
    }

    init {
        executors = listOf(eventListener, captureNetwork, sessionReplay)
    }

    fun shutdown() {
        executors.forEach { it.shutdown() }
    }
}