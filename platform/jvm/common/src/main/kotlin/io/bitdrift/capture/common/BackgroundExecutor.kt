package io.bitdrift.capture.common

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Single thread Background executor
 */
object BackgroundExecutor {

    private val executorService by lazy {
        Executors.newSingleThreadExecutor {
            Thread(it, "io.bitdrift.capture.event-listener-2")
        }
    }

    fun get(): ExecutorService {
        return executorService
    }
}