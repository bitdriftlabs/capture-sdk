// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

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
        ;

        /** A request of this type with the app's default settings; a heap dump ignores [durationMs]. */
        fun request(durationMs: Int): Request =
            when (this) {
                JAVA_HEAP_DUMP -> Request.JavaHeapDump
                HEAP_PROFILE -> Request.HeapProfile(durationMs)
                STACK_SAMPLING -> Request.StackSampling(durationMs)
                SYSTEM_TRACE -> Request.SystemTrace(durationMs)
            }
    }

    /** One capture with the settings its type supports. The platform clamps each value to its own bounds. */
    sealed interface Request {
        val type: ProfilingType

        /** A single snapshot of the Java heap. */
        data object JavaHeapDump : Request {
            override val type = ProfilingType.JAVA_HEAP_DUMP
        }

        data class HeapProfile(
            val durationMs: Int,
            val bufferSizeKb: Int = HEAP_PROFILE_BUFFER_SIZE_KB,
            val samplingIntervalBytes: Long = HEAP_PROFILE_SAMPLING_INTERVAL_BYTES,
            /**
             * Records Java heap allocations instead of native ones; a profile cannot record both. Native
             * by default, which the `system_trace` symbolication workload relies on.
             */
            val trackJavaAllocations: Boolean = false,
        ) : Request {
            override val type = ProfilingType.HEAP_PROFILE
        }

        data class StackSampling(
            val durationMs: Int,
            val bufferSizeKb: Int = STACK_SAMPLING_BUFFER_SIZE_KB,
            val frequencyHz: Int = STACK_SAMPLING_FREQUENCY_HZ,
        ) : Request {
            override val type = ProfilingType.STACK_SAMPLING
        }

        data class SystemTrace(
            val durationMs: Int,
        ) : Request {
            override val type = ProfilingType.SYSTEM_TRACE
        }
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

    suspend fun capture(
        context: Context,
        request: Request,
    ): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return Result.Failure("requires API 35, device is API ${Build.VERSION.SDK_INT}")
        }
        return requestProfile(context, request)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun requestProfile(
        context: Context,
        request: Request,
    ): Result {
        val profilingResult =
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                requestProfiling(
                    context,
                    request.toPlatformRequest(cancellationSignal),
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
    private fun Request.toPlatformRequest(cancellationSignal: CancellationSignal): ProfilingRequest =
        when (this) {
            Request.JavaHeapDump ->
                JavaHeapDumpRequestBuilder()
                    .setCancellationSignal(cancellationSignal)
                    .build()
            is Request.HeapProfile ->
                HeapProfileRequestBuilder()
                    .setBufferSizeKb(bufferSizeKb)
                    .setSamplingIntervalBytes(samplingIntervalBytes)
                    .setTrackJavaAllocations(trackJavaAllocations)
                    .setDurationMs(durationMs)
                    .setCancellationSignal(cancellationSignal)
                    .build()
            is Request.StackSampling ->
                StackSamplingRequestBuilder()
                    .setBufferSizeKb(bufferSizeKb)
                    .setDurationMs(durationMs)
                    .setSamplingFrequencyHz(frequencyHz)
                    .setCancellationSignal(cancellationSignal)
                    .build()
            is Request.SystemTrace ->
                SystemTraceRequestBuilder()
                    .setDurationMs(durationMs)
                    .setCancellationSignal(cancellationSignal)
                    .build()
        }

    private const val HEAP_PROFILE_BUFFER_SIZE_KB = 8192
    private const val HEAP_PROFILE_SAMPLING_INTERVAL_BYTES = 128L * 1024L
    private const val STACK_SAMPLING_BUFFER_SIZE_KB = 4096
    private const val STACK_SAMPLING_FREQUENCY_HZ = 100
}
