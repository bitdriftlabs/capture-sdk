// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.profiling

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ProfilingManager
import android.os.ProfilingResult
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.utils.BuildVersionChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.function.Consumer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfilerTest {
    private val manager: ProfilingManager = mock()
    private val context: Context = mock { on { getSystemService(ProfilingManager::class.java) } doReturn manager }
    private val buildVersionChecker: BuildVersionChecker = mock { on { isAtLeast(35) } doReturn true }
    private val outcomes = mutableListOf<ProfileOutcome>()
    private val profiler = Profiler(context, Runnable::run, buildVersionChecker)

    @Test
    fun belowAndroid15ReportsUnsupportedWithoutTouchingThePlatform() {
        whenever(buildVersionChecker.isAtLeast(35)).thenReturn(false)

        profiler.request(REQUEST) { outcomes += it }

        assertThat(outcomes).containsExactly(
            ProfileOutcome.Failed(0, "unsupported", "ProfilingManager requires Android 15 or newer"),
        )
        verifyNoInteractions(context)
    }

    @Test
    fun aDeviceWithoutTheProfilerReportsUnsupported() {
        whenever(context.getSystemService(ProfilingManager::class.java)).thenReturn(null)

        profiler.request(REQUEST) { outcomes += it }

        assertThat(outcomes).containsExactly(
            ProfileOutcome.Failed(0, "unsupported", "ProfilingManager is not available on this device"),
        )
    }

    @Test
    fun requestsAStackSampleWithEveryParameterAndTheTag() {
        profiler.request(REQUEST) { outcomes += it }

        val params = argumentCaptor<Bundle>()
        verify(manager).requestProfiling(
            eq(ProfilingManager.PROFILING_TYPE_STACK_SAMPLING),
            params.capture(),
            eq("bitdrift"),
            any(),
            any(),
            any(),
        )
        val values = params.firstValue.let { bundle -> bundle.keySet().map { bundle.getInt(it) } }
        assertThat(values).contains(3_000, 100, 2_048)
        assertThat(outcomes).isEmpty()
    }

    @Test
    fun aSavedTraceBecomesItsPath() {
        profiler.request(REQUEST) { outcomes += it }

        deliver(result(ProfilingResult.ERROR_NONE, path = "/data/misc/profiling/trace.perfetto-stack-sample"))

        assertThat(outcomes).containsExactly(ProfileOutcome.Saved("/data/misc/profiling/trace.perfetto-stack-sample"))
    }

    @Test
    fun aProfilerErrorKeepsItsCodeNameAndMessage() {
        profiler.request(REQUEST) { outcomes += it }

        deliver(result(ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS, message = "too many requests"))

        assertThat(outcomes).containsExactly(
            ProfileOutcome.Failed(ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS, "FAILED_RATE_LIMIT_PROCESS", "too many requests"),
        )
    }

    @Test
    fun successWithoutAFileIsAFailure() {
        profiler.request(REQUEST) { outcomes += it }

        deliver(result(ProfilingResult.ERROR_NONE, path = null))

        assertThat(outcomes).containsExactly(ProfileOutcome.Failed(0, "no_file", "the profiler reported success without a file"))
    }

    @Test
    fun cancellingCancelsThePlatformRequest() {
        val cancel = profiler.request(REQUEST) { outcomes += it }

        val signal = argumentCaptor<CancellationSignal>()
        verify(manager).requestProfiling(any(), any(), any(), signal.capture(), any(), any())
        assertThat(signal.firstValue.isCanceled).isFalse()

        cancel.cancel()

        assertThat(signal.firstValue.isCanceled).isTrue()
    }

    private fun deliver(result: ProfilingResult) {
        val listener = argumentCaptor<Consumer<ProfilingResult>>()
        verify(manager).requestProfiling(any(), any(), any(), any(), any(), listener.capture())
        listener.firstValue.accept(result)
    }

    private fun result(
        errorCode: Int,
        path: String? = null,
        message: String? = null,
    ): ProfilingResult =
        mock {
            on { this.errorCode } doReturn errorCode
            on { resultFilePath } doReturn path
            on { errorMessage } doReturn message
        }

    private companion object {
        val REQUEST =
            ProfileRequest(
                type = ProfileType.STACK_SAMPLING,
                durationMs = 3_000,
                frequencyHz = 100,
                bufferSizeKb = 2_048,
                tag = "bitdrift",
            )
    }
}
