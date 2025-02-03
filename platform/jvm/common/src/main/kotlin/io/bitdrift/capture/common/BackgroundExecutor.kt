package io.bitdrift.capture.common

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A single threaded [ExecutorService] that can be used to run an specific block of code
 */
object BackgroundExecutor {

    private const val SINGLE_THREAD_EXECUTOR_NAME = "io.bitdrift.capture.single-background-executor"

    private val singleThreadExecutor by lazy {
        Executors.newSingleThreadExecutor {
            Thread(it, SINGLE_THREAD_EXECUTOR_NAME)
        }
    }

    /**
     * Run the specified action on a single threaded background executor
     */
    fun runAction(action: () -> Unit) {
        singleThreadExecutor.execute(action)
    }
}