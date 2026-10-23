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
import android.os.ProfilingManager
import android.os.ProfilingResult
import androidx.annotation.RequiresApi
import androidx.core.os.ProfilingRequest
import androidx.core.os.StackSamplingRequestBuilder
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Runs captures through android.os.ProfilingManager, building each request with androidx.core's
 * request builders. Every parameter is always set: the platform's own defaults are not documented.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingManagerProfiler(
    context: Context,
    private val executor: Executor,
) : IProfiler {
    // One system service lookup for the life of the logger; null when the device has no profiler.
    private val manager: ProfilingManager? by lazy { context.getSystemService(ProfilingManager::class.java) }

    override fun request(
        request: ProfileRequest,
        callback: (ProfileOutcome) -> Unit,
    ): IProfilingCancel {
        val manager = manager
        if (manager == null) {
            callback(ProfileOutcome.Failed(0, "unsupported", "ProfilingManager is not available on this device"))
            return IProfilingCancel {}
        }
        val signal = CancellationSignal()
        val platformRequest = request.toPlatformRequest(signal)
        manager.requestProfiling(
            platformRequest.profilingType,
            platformRequest.params,
            platformRequest.tag,
            platformRequest.cancellationSignal,
            executor,
            Consumer<ProfilingResult> { result -> callback(result.toOutcome()) },
        )
        return IProfilingCancel { signal.cancel() }
    }

    private fun ProfileRequest.toPlatformRequest(signal: CancellationSignal): ProfilingRequest =
        when (type) {
            ProfileType.STACK_SAMPLING ->
                StackSamplingRequestBuilder()
                    .setDurationMs(durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS).toInt())
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

        // The core bounds the duration before it reaches this class (10 s by default, and never
        // more than its execution timeout allows). These only guard against a misconfigured
        // runtime flag, so they are deliberately wide.
        const val MIN_DURATION_MS = 1_000L
        const val MAX_DURATION_MS = 60_000L
    }
}
