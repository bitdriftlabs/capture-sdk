// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import io.bitdrift.capture.common.IBackgroundThreadHandler
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Fake [IBackgroundThreadHandler] that queues tasks instead of running them right away.
 *
 * Unlike [FakeBackgroundThreadHandler], which runs tasks inline on the calling thread, this fake
 * keeps the window between "task scheduled" and "task completed" open for as long as the test
 * needs it, so tests can observe the state a component is in while its async work is still
 * pending.
 */
class DeferredBackgroundThreadHandler : IBackgroundThreadHandler {
    private val pendingTasks = ConcurrentLinkedQueue<() -> Unit>()

    /**
     * Whether any scheduled task is still waiting to be run.
     */
    val hasPendingTasks: Boolean
        get() = pendingTasks.isNotEmpty()

    override fun runAsync(task: () -> Unit) {
        pendingTasks.add(task)
    }

    /**
     * Runs every queued task on the calling thread.
     */
    fun runPendingOnCurrentThread() {
        drainPendingTasks().forEach { it() }
    }

    /**
     * Runs every queued task on a newly started thread, mirroring production where the work lands
     * on a background worker rather than the caller. Returns the thread so callers can join it.
     */
    fun runPendingOnNewThread(name: String = "fake-background-thread-worker"): Thread {
        val tasks = drainPendingTasks()
        return Thread({ tasks.forEach { it() } }, name).apply { start() }
    }

    private fun drainPendingTasks(): List<() -> Unit> = generateSequence { pendingTasks.poll() }.toList()
}
