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
import androidx.core.os.HeapProfileRequestBuilder
import androidx.core.os.JavaHeapDumpRequestBuilder
import androidx.core.os.ProfilingRequest
import androidx.core.os.StackSamplingRequestBuilder
import androidx.core.os.SystemTraceRequestBuilder
import androidx.core.os.requestProfiling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.time.Duration

/**
 * Captures a profile of this process through the platform ProfilingManager (API 35+). The system
 * writes the result into the app's own storage and hands back its path.
 */
object AppProfiler {
    enum class ProfilingType(
        val displayName: String,
        val usesDuration: Boolean,
    ) {
        JAVA_HEAP_DUMP("Java heap dump", usesDuration = false),
        HEAP_PROFILE("Heap profile", usesDuration = true),
        STACK_SAMPLING("Stack sampling", usesDuration = true),
        SYSTEM_TRACE("System trace", usesDuration = true),
    }

    sealed interface Result {
        class Saved(
            val path: String,
        ) : Result

        class Failure(
            val reason: String,
        ) : Result
    }

    /** [duration] is ignored for [ProfilingType.JAVA_HEAP_DUMP], which is a single snapshot. */
    suspend fun capture(
        context: Context,
        type: ProfilingType,
        duration: Duration,
    ): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return Result.Failure("requires API 35, device is API ${Build.VERSION.SDK_INT}")
        }
        return request(context, type, duration)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun request(
        context: Context,
        type: ProfilingType,
        duration: Duration,
    ): Result {
        val profilingResult =
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                requestProfiling(
                    context,
                    buildRequest(type, duration.inWholeMilliseconds.toInt(), cancellationSignal),
                    Dispatchers.IO.asExecutor(),
                    Consumer<ProfilingResult> { continuation.resume(it) },
                )
            }

        if (profilingResult.errorCode != ProfilingResult.ERROR_NONE) {
            return Result.Failure("errorCode=${profilingResult.errorCode} ${profilingResult.errorMessage.orEmpty()}")
        }
        return profilingResult.resultFilePath?.let(Result::Saved) ?: Result.Failure("no profile file produced")
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun buildRequest(
        type: ProfilingType,
        durationMs: Int,
        cancellationSignal: CancellationSignal,
    ): ProfilingRequest =
        when (type) {
            ProfilingType.JAVA_HEAP_DUMP ->
                JavaHeapDumpRequestBuilder()
                    .setCancellationSignal(cancellationSignal)
                    .build()
            ProfilingType.HEAP_PROFILE ->
                HeapProfileRequestBuilder()
                    .setDurationMs(durationMs)
                    .setCancellationSignal(cancellationSignal)
                    .build()
            ProfilingType.STACK_SAMPLING ->
                StackSamplingRequestBuilder()
                    .setBufferSizeKb(STACK_SAMPLING_BUFFER_SIZE_KB)
                    .setDurationMs(durationMs)
                    .setSamplingFrequencyHz(STACK_SAMPLING_FREQUENCY_HZ)
                    .setCancellationSignal(cancellationSignal)
                    .build()
            ProfilingType.SYSTEM_TRACE ->
                SystemTraceRequestBuilder()
                    .setDurationMs(durationMs)
                    .setCancellationSignal(cancellationSignal)
                    .build()
        }

    private const val STACK_SAMPLING_BUFFER_SIZE_KB = 4096
    private const val STACK_SAMPLING_FREQUENCY_HZ = 100
}
