// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Pins the platform contract of the built-in stack sampling command: one capture at a time, fail
 * fast when the process is not profileable, and exactly one completion per request.
 */
class ProfilingTargetTest {
    private class FakeProfiler : IProfiler {
        val requests = mutableListOf<ProfileRequest>()
        val callbacks = mutableListOf<(ProfileOutcome) -> Unit>()
        val cancelled = mutableListOf<Long>()

        override fun request(
            request: ProfileRequest,
            callback: (ProfileOutcome) -> Unit,
        ): IProfilingCancel {
            requests += request
            callbacks += callback
            return IProfilingCancel { cancelled += request.requestId }
        }

        fun complete(outcome: ProfileOutcome) = callbacks.removeAt(0)(outcome)
    }

    private class RecordingBridge : IProfilingBridge {
        data class Completion(
            val requestId: Long,
            val path: String?,
            val errorCode: Int,
            val errorName: String?,
            val errorMessage: String?,
        )

        val completions = mutableListOf<Completion>()

        override fun completeStackSample(
            requestId: Long,
            path: String?,
            errorCode: Int,
            errorName: String?,
            errorMessage: String?,
        ) {
            completions += Completion(requestId, path, errorCode, errorName, errorMessage)
        }
    }

    private val profiler = FakeProfiler()
    private val bridge = RecordingBridge()
    private var profileable = true
    private val target =
        ProfilingTarget(
            profiler = profiler,
            profileability = { profileable },
            background = FakeBackgroundThreadHandler(),
            bridge = bridge,
            logger = mock<IInternalLogger>(),
        )

    @Test
    fun jniContractUsesPrimitiveSignatures() {
        val capture =
            IProfilingTarget::class.java.getMethod(
                "captureStackSample",
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                Integer.TYPE,
                Integer.TYPE,
            )
        assertThat(capture.returnType).isEqualTo(java.lang.Void.TYPE)
        val cancel = IProfilingTarget::class.java.getMethod("cancelStackSample", java.lang.Long.TYPE)
        assertThat(cancel.returnType).isEqualTo(java.lang.Void.TYPE)
    }

    @Test
    fun savedCaptureReportsItsPath() {
        target.captureStackSample(7, 3_000, 100, 2_048)

        assertThat(profiler.requests).containsExactly(ProfileRequest(7, ProfileType.STACK_SAMPLING, 3_000, 100, 2_048))
        profiler.complete(ProfileOutcome.Saved("/data/user/0/app/files/profiling/trace"))

        assertThat(bridge.completions).containsExactly(
            RecordingBridge.Completion(7, "/data/user/0/app/files/profiling/trace", 0, null, null),
        )
    }

    @Test
    fun failedCaptureReportsTheProfilerError() {
        target.captureStackSample(7, 3_000, 100, 2_048)
        profiler.complete(ProfileOutcome.Failed(2, "ERROR_FAILED_RATE_LIMIT_PROCESS", "rate limited"))

        assertThat(bridge.completions).containsExactly(
            RecordingBridge.Completion(7, null, 2, "ERROR_FAILED_RATE_LIMIT_PROCESS", "rate limited"),
        )
    }

    @Test
    fun nonProfileableProcessFailsWithoutCallingTheProfiler() {
        profileable = false

        target.captureStackSample(7, 3_000, 100, 2_048)

        assertThat(profiler.requests).isEmpty()
        assertThat(bridge.completions).hasSize(1)
        assertThat(bridge.completions[0].requestId).isEqualTo(7)
        assertThat(bridge.completions[0].path).isNull()
        assertThat(bridge.completions[0].errorName).isEqualTo("not_profileable")
    }

    @Test
    fun secondCaptureIsRejectedWhileOneIsRunning() {
        target.captureStackSample(1, 3_000, 100, 2_048)
        target.captureStackSample(2, 3_000, 100, 2_048)

        assertThat(profiler.requests).hasSize(1)
        assertThat(bridge.completions.map { it.requestId to it.errorName }).containsExactly(2L to "already_executing")

        profiler.complete(ProfileOutcome.Saved("/trace"))
        target.captureStackSample(3, 1_000, 50, 256)

        assertThat(profiler.requests.map { it.requestId }).containsExactly(1L, 3L)
    }

    @Test
    fun cancelStopsTheRunningCaptureAndItsLateOutcomeIsStillReportedOnce() {
        target.captureStackSample(7, 3_000, 100, 2_048)
        target.cancelStackSample(8)
        assertThat(profiler.cancelled).isEmpty()

        target.cancelStackSample(7)
        assertThat(profiler.cancelled).containsExactly(7L)

        // The platform reports the stopped capture; the core ignores it, but the slot must be free.
        profiler.complete(ProfileOutcome.Failed(0, "cancelled", "cancelled"))
        assertThat(bridge.completions.map { it.requestId }).containsExactly(7L)
        target.captureStackSample(9, 3_000, 100, 2_048)
        assertThat(profiler.requests.map { it.requestId }).containsExactly(7L, 9L)
    }

    @Test
    fun noopTargetFailsAsUnsupported() {
        NoopProfilingTarget(bridge).captureStackSample(4, 3_000, 100, 2_048)

        assertThat(bridge.completions).hasSize(1)
        assertThat(bridge.completions[0].requestId).isEqualTo(4)
        assertThat(bridge.completions[0].errorName).isEqualTo("unsupported")
    }
}
