// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.ProfilingResult
import androidx.annotation.RequiresApi
import androidx.core.os.ProfilingRequest
import androidx.core.os.StackSamplingRequestBuilder
import androidx.core.os.requestProfiling
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Runs captures through android.os.ProfilingManager by way of androidx.core's request builders.
 * Every parameter is always set: the platform's own defaults are not documented.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingManagerProfiler(
    private val context: Context,
    private val executor: Executor,
) : IProfiler {
    override fun request(
        request: ProfileRequest,
        callback: (ProfileOutcome) -> Unit,
    ): IProfilingCancel {
        val signal = CancellationSignal()
        requestProfiling(
            context,
            request.toPlatformRequest(signal),
            executor,
            Consumer<ProfilingResult> { result -> callback(result.toOutcome()) },
        )
        return IProfilingCancel { signal.cancel() }
    }

    private fun ProfileRequest.toPlatformRequest(signal: CancellationSignal): ProfilingRequest =
        when (type) {
            ProfileType.STACK_SAMPLING ->
                StackSamplingRequestBuilder()
                    .setDurationMs(durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    .setSamplingFrequencyHz(frequencyHz)
                    .setBufferSizeKb(bufferSizeKb)
                    .setTag(TAG)
                    .setCancellationSignal(signal)
                    .build()
        }

    private fun ProfilingResult.toOutcome(): ProfileOutcome {
        if (errorCode != ProfilingResult.ERROR_NONE) {
            return ProfileOutcome.Failed(errorCode, ProfilingErrorCode.name(errorCode), errorMessage.orEmpty())
        }
        val path = resultFilePath
        if (path.isNullOrEmpty()) {
            return ProfileOutcome.Failed(0, "no_file", "the profiler reported success without a file")
        }
        return ProfileOutcome.Saved(path)
    }

    private companion object {
        // The output file's name prefix; used to recognise the SDK's own results.
        const val TAG = "bitdrift"
    }
}
