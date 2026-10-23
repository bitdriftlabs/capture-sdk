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

/**
 * Runs one stack sample at a time, fails fast when the process cannot be profiled, and reports
 * every outcome to the core through [IProfilingBridge].
 */
internal class ProfilingTarget(
    private val profiler: IProfiler,
    private val profileability: IProfileabilityChecker,
    private val background: IBackgroundThreadHandler,
    private val bridge: IProfilingBridge,
    private val logger: IInternalLogger,
) : IProfilingTarget {
    private class Active(
        val requestId: Long,
    ) {
        @Volatile
        var cancel: IProfilingCancel? = null
    }

    private val lock = Any()
    private var active: Active? = null

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
                logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG) { "stack sample $requestId saved to ${outcome.path}" }
                bridge.completeStackSample(requestId, outcome.path, 0, null, null)
            }
            is ProfileOutcome.Failed -> {
                logger.logInternal(LogType.INTERNALSDK, LogLevel.WARNING) {
                    "stack sample $requestId failed: ${outcome.name} (${outcome.code}) ${outcome.message}"
                }
                bridge.completeStackSample(requestId, null, outcome.code, outcome.name, outcome.message)
            }
        }
    }
}
