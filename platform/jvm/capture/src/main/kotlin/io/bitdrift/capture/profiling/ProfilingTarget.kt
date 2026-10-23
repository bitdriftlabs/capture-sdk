// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.common.IBackgroundThreadHandler
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one stack sample at a time, fails fast when the process cannot be profiled, and reports
 * every outcome to the core through [IProfilingBridge]. A finished trace is moved into
 * [traceDirectory], the SDK's own storage, before the core is told about it: the system writes
 * every profile the app ever requests into one shared directory, so location, not the profiler's
 * tag, is what marks a trace as the SDK's.
 */
internal class ProfilingTarget(
    private val profiler: IProfiler,
    private val profileability: IProfileabilityChecker,
    private val background: IBackgroundThreadHandler,
    private val bridge: IProfilingBridge,
    private val logger: IInternalLogger,
    private val traceDirectory: File,
) : IProfilingTarget {
    private class Active(
        val requestId: Long,
    ) {
        @Volatile
        var cancel: IProfilingCancel? = null
    }

    private val lock = Any()
    private var active: Active? = null
    private val swept = AtomicBoolean(false)

    override fun captureStackSample(
        requestId: Long,
        durationMs: Long,
        frequencyHz: Int,
        bufferSizeKb: Int,
    ) {
        // The core calls this on its runtime thread; the profileability check and the binder call
        // to ProfilingManager belong on the SDK's background thread.
        background.runAsync {
            start(ProfileRequest(requestId, ProfileType.STACK_SAMPLING, durationMs, frequencyHz, bufferSizeKb))
        }
    }

    override fun cancelStackSample(requestId: Long) {
        val cancel = synchronized(lock) { active?.takeIf { it.requestId == requestId }?.cancel }
        cancel?.cancel()
    }

    private fun start(request: ProfileRequest) {
        if (swept.compareAndSet(false, true)) sweepOrphans()
        if (!profileability.isProfileable()) {
            complete(
                request.requestId,
                ProfileOutcome.Failed(
                    0,
                    "not_profileable",
                    "the app is not profileable; set <profileable android:enabled=\"true\" /> or use a debuggable build",
                ),
            )
            return
        }
        val pending = Active(request.requestId)
        val claimed = synchronized(lock) { (active == null).also { if (it) active = pending } }
        if (!claimed) {
            complete(
                request.requestId,
                ProfileOutcome.Failed(0, "already_executing", "a stack sampling capture is already in progress"),
            )
            return
        }
        logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG) {
            "starting stack sample ${request.requestId}: ${request.durationMs} ms at ${request.frequencyHz} Hz"
        }
        pending.cancel = profiler.request(request) { outcome -> complete(request.requestId, outcome) }
    }

    private fun complete(
        requestId: Long,
        outcome: ProfileOutcome,
    ) {
        synchronized(lock) {
            if (active?.requestId == requestId) active = null
        }
        when (outcome) {
            is ProfileOutcome.Saved -> {
                val path = claim(outcome.path)
                logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG) { "stack sample $requestId saved to $path" }
                bridge.completeStackSample(requestId, path, 0, null, null)
            }
            is ProfileOutcome.Failed -> {
                logger.logInternal(LogType.INTERNALSDK, LogLevel.WARNING) {
                    "stack sample $requestId failed: ${outcome.name} (${outcome.code}) ${outcome.message}"
                }
                bridge.completeStackSample(requestId, null, outcome.code, outcome.name, outcome.message)
            }
        }
    }

    /**
     * Deletes traces an earlier process left behind: a trace stays in [traceDirectory] only between
     * its move and the core persisting its upload, so one that is still there minutes later belongs
     * to a process that died in between. Runs once per process, on the first capture, so the SDK
     * does no disk work at startup. Recent files are left alone: a capture that finished moments ago
     * may still be read by the uploader.
     */
    private fun sweepOrphans() {
        val cutoff = System.currentTimeMillis() - ORPHAN_AGE_MS
        val orphans = traceDirectory.listFiles { file -> file.isFile && file.lastModified() < cutoff } ?: return
        val deleted = orphans.count { it.delete() }
        if (deleted > 0) {
            logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG) { "deleted $deleted orphaned stack sampling trace(s)" }
        }
    }

    /** Moves the trace into [traceDirectory]; a trace that cannot be moved stays where the system wrote it. */
    private fun claim(path: String): String {
        val source = File(path)
        val target = File(traceDirectory, source.name)
        val moved = (traceDirectory.isDirectory || traceDirectory.mkdirs()) && source.renameTo(target)
        return if (moved) target.path else path
    }

    private companion object {
        const val ORPHAN_AGE_MS = 10 * 60 * 1000L
    }
}
