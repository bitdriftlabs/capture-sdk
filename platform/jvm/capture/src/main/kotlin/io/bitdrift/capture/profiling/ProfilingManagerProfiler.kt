// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ProfilingManager
import android.os.ProfilingResult
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Runs captures through android.os.ProfilingManager. The parameters are written under the Bundle
 * keys androidx.core's request builders use, so no androidx.core upgrade is needed. All three are
 * always set: the platform's own defaults are not documented.
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
        val manager = context.getSystemService(ProfilingManager::class.java)
        if (manager == null) {
            callback(ProfileOutcome.Failed(0, "unsupported", "ProfilingManager is not available"))
            return IProfilingCancel {}
        }
        val parameters =
            Bundle().apply {
                putInt(KEY_DURATION_MS, request.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                putInt(KEY_FREQUENCY_HZ, request.frequencyHz)
                putInt(KEY_SIZE_KB, request.bufferSizeKb)
            }
        val signal = CancellationSignal()
        manager.requestProfiling(
            request.type.platformType(),
            parameters,
            TAG,
            signal,
            executor,
            Consumer<ProfilingResult> { result -> callback(result.toOutcome()) },
        )
        return IProfilingCancel { signal.cancel() }
    }

    private fun ProfileType.platformType(): Int =
        when (this) {
            ProfileType.STACK_SAMPLING -> ProfilingManager.PROFILING_TYPE_STACK_SAMPLING
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
        const val KEY_DURATION_MS = "KEY_DURATION_MS"
        const val KEY_FREQUENCY_HZ = "KEY_FREQUENCY_HZ"
        const val KEY_SIZE_KB = "KEY_SIZE_KB"
    }
}
