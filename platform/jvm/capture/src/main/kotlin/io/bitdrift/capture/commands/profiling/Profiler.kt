// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.profiling

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.ProfilingManager
import android.os.ProfilingResult
import androidx.annotation.RequiresApi
import androidx.core.os.ProfilingRequest
import androidx.core.os.StackSamplingRequestBuilder
import io.bitdrift.capture.utils.BuildVersionChecker
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Runs captures through android.os.ProfilingManager, building each request with androidx.core's
 * request builders. Every sampling parameter is always set: the platform's own defaults are not
 * documented. The request tag marks the capture as the SDK's, so an app can recognise it among
 * its own profiling results. Below Android 15, where `ProfilingManager` does not exist, every
 * request fails as `unsupported`.
 */
internal class Profiler(
    private val context: Context,
    private val executor: Executor,
    private val buildVersionChecker: BuildVersionChecker = BuildVersionChecker(),
) : IProfiler {
    override fun request(
        request: ProfileRequest,
        callback: (ProfileOutcome) -> Unit,
    ): ICancellable {
        if (!buildVersionChecker.isAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)) {
            callback(ProfileOutcome.Failed(0, "unsupported", "ProfilingManager requires Android 15 or newer"))
            return ICancellable {}
        }
        return requestProfiling(request, callback)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun requestProfiling(
        request: ProfileRequest,
        callback: (ProfileOutcome) -> Unit,
    ): ICancellable {
        val manager = context.getSystemService(ProfilingManager::class.java)
        if (manager == null) {
            callback(ProfileOutcome.Failed(0, "unsupported", "ProfilingManager is not available on this device"))
            return ICancellable {}
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
        return ICancellable { signal.cancel() }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun ProfileRequest.toPlatformRequest(signal: CancellationSignal): ProfilingRequest =
        when (type) {
            ProfileType.STACK_SAMPLING ->
                StackSamplingRequestBuilder()
                    .setDurationMs(durationMs.toInt())
                    .setSamplingFrequencyHz(frequencyHz)
                    .setBufferSizeKb(bufferSizeKb)
                    .setTag(tag)
                    .setCancellationSignal(signal)
                    .build()
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun ProfilingResult.toOutcome(): ProfileOutcome {
        if (errorCode != ProfilingResult.ERROR_NONE) {
            return ProfileOutcome.Failed(errorCode, errorCode.toErrorName(), errorMessage.orEmpty())
        }
        val path = resultFilePath
        if (path.isNullOrEmpty()) {
            return ProfileOutcome.Failed(0, "no_file", "the profiler reported success without a file")
        }
        return ProfileOutcome.Saved(path)
    }

    /** The ProfilingResult.ERROR_* constant for a code, without the redundant prefix. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun Int.toErrorName(): String =
        when (this) {
            ProfilingResult.ERROR_FAILED_RATE_LIMIT_SYSTEM -> "FAILED_RATE_LIMIT_SYSTEM"
            ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS -> "FAILED_RATE_LIMIT_PROCESS"
            ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS -> "FAILED_PROFILING_IN_PROGRESS"
            ProfilingResult.ERROR_FAILED_EXECUTING -> "FAILED_EXECUTING"
            ProfilingResult.ERROR_FAILED_POST_PROCESSING -> "FAILED_POST_PROCESSING"
            ProfilingResult.ERROR_FAILED_NO_DISK_SPACE -> "FAILED_NO_DISK_SPACE"
            ProfilingResult.ERROR_FAILED_INVALID_REQUEST -> "FAILED_INVALID_REQUEST"
            ProfilingResult.ERROR_UNKNOWN -> "UNKNOWN"
            else -> "UNRECOGNIZED"
        }
}
