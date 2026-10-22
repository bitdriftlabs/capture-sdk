// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.os.Trace
import io.bitdrift.gradletestapp.diagnostics.fatalissues.NativeThreads
import java.util.concurrent.atomic.AtomicBoolean

internal class PerfettoWorkload private constructor(
    private val running: AtomicBoolean,
    private val threads: List<Thread>,
    private var graph: PerfettoHeapNode?,
) : AutoCloseable {
    override fun close() {
        running.set(false)
        threads.forEach { it.join() }
        // Consume the graph so R8 must retain its objects and reference fields across capture.
        var node = graph
        while (node != null) {
            result += node.payload.size
            node = node.next
        }
        graph = null
    }

    companion object {
        @Volatile private var result = 0L

        fun start(
            type: AppProfiler.ProfilingType,
            durationMs: Long,
        ): PerfettoWorkload {
            val running = AtomicBoolean(true)
            val deadline = System.nanoTime() + (durationMs + 5_000) * 1_000_000
            val graph =
                if (type == AppProfiler.ProfilingType.JAVA_HEAP_DUMP) {
                    (0 until 1_024).fold<Int, PerfettoHeapNode?>(null) { next, _ ->
                        PerfettoHeapNode(ByteArray(1_024), next)
                    }
                } else {
                    null
                }
            val threads =
                when (type) {
                    AppProfiler.ProfilingType.STACK_SAMPLING ->
                        listOf(
                            Thread({ perfettoJavaCpuWorkload(durationMs.coerceIn(0, 3_000) + 1_000) }, "perfetto-java"),
                            Thread({ NativeThreads.runCpuWorkload(durationMs.coerceIn(0, 3_000) + 1_000) }, "perfetto-native"),
                        )
                    AppProfiler.ProfilingType.HEAP_PROFILE ->
                        listOf(
                            Thread({
                                while (running.get() && System.nanoTime() < deadline) {
                                    NativeThreads.runAllocationWorkload(100)
                                }
                            }, "perfetto-alloc"),
                        )
                    AppProfiler.ProfilingType.SYSTEM_TRACE ->
                        listOf(
                            Thread({
                                while (running.get() && System.nanoTime() < deadline) {
                                    Trace.beginSection("bitdrift-perfetto-system-probe")
                                    try {
                                        Thread.sleep(20)
                                    } finally {
                                        Trace.endSection()
                                    }
                                }
                            }, "perfetto-events"),
                        )
                    AppProfiler.ProfilingType.JAVA_HEAP_DUMP -> emptyList()
                }
            return PerfettoWorkload(running, threads, graph).also { threads.forEach(Thread::start) }
        }

        private fun perfettoJavaCpuWorkload(durationMs: Long) {
            val deadline = System.nanoTime() + durationMs * 1_000_000
            var accumulator = 1L
            do {
                repeat(10_000) { accumulator = accumulator * 1_664_525 + 1_013_904_223 }
            } while (System.nanoTime() < deadline)
            result = accumulator
        }
    }
}

private class PerfettoHeapNode(
    val payload: ByteArray,
    val next: PerfettoHeapNode?,
)
