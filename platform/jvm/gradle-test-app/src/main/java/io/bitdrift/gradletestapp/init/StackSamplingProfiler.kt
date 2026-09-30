// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.ProfilingResult
import androidx.annotation.RequiresApi
import androidx.core.os.StackSamplingRequestBuilder
import androidx.core.os.requestProfiling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.time.Duration

/**
 * Captures a Perfetto call stack sampling trace of this process through the platform ProfilingManager.
 */
object StackSamplingProfiler {
    sealed interface Result {
        class Trace(
            val bytes: ByteArray,
        ) : Result

        class Failure(
            val reason: String,
        ) : Result
    }

    suspend fun capture(
        context: Context,
        duration: Duration,
    ): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return Result.Failure("requires API 35, device is API ${Build.VERSION.SDK_INT}")
        }
        return requestStackSampling(context, duration)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun requestStackSampling(
        context: Context,
        duration: Duration,
    ): Result {
        val profilingResult =
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                requestProfiling(
                    context,
                    StackSamplingRequestBuilder()
                        .setBufferSizeKb(BUFFER_SIZE_KB)
                        .setDurationMs(duration.inWholeMilliseconds.toInt())
                        .setSamplingFrequencyHz(SAMPLING_FREQUENCY_HZ)
                        .setCancellationSignal(cancellationSignal)
                        .build(),
                    Dispatchers.IO.asExecutor(),
                    Consumer<ProfilingResult> { continuation.resume(it) },
                )
            }

        if (profilingResult.errorCode != ProfilingResult.ERROR_NONE) {
            return Result.Failure("errorCode=${profilingResult.errorCode} ${profilingResult.errorMessage.orEmpty()}")
        }
        val path = profilingResult.resultFilePath ?: return Result.Failure("no trace file produced")
        return withContext(Dispatchers.IO) {
            File(path).takeIf(File::exists)?.readBytes()?.let(Result::Trace)
                ?: Result.Failure("trace file missing at $path")
        }
    }

    private const val BUFFER_SIZE_KB = 4096
    private const val SAMPLING_FREQUENCY_HZ = 100
}
