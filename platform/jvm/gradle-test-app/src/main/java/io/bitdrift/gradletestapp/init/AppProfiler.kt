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

        /**
         * [errorCode] and [errorName] are the system's [ProfilingResult] error, and are null when the
         * capture failed before the system profiler answered.
         */
        class Failure(
            val message: String,
            val errorCode: Int? = null,
            val errorName: String? = null,
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
            val errorCode = profilingResult.errorCode
            return Result.Failure(profilingResult.errorMessage.orEmpty(), errorCode, errorCodeName(errorCode))
        }
        return profilingResult.resultFilePath?.let(Result::Saved) ?: Result.Failure("no profile file produced")
    }

    private fun errorCodeName(errorCode: Int): String =
        when (errorCode) {
            ProfilingResult.ERROR_FAILED_RATE_LIMIT_SYSTEM -> "ERROR_FAILED_RATE_LIMIT_SYSTEM"
            ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS -> "ERROR_FAILED_RATE_LIMIT_PROCESS"
            ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS -> "ERROR_FAILED_PROFILING_IN_PROGRESS"
            ProfilingResult.ERROR_FAILED_EXECUTING -> "ERROR_FAILED_EXECUTING"
            ProfilingResult.ERROR_FAILED_POST_PROCESSING -> "ERROR_FAILED_POST_PROCESSING"
            ProfilingResult.ERROR_FAILED_NO_DISK_SPACE -> "ERROR_FAILED_NO_DISK_SPACE"
            ProfilingResult.ERROR_FAILED_INVALID_REQUEST -> "ERROR_FAILED_INVALID_REQUEST"
            ProfilingResult.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            else -> "UNRECOGNIZED_ERROR"
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
