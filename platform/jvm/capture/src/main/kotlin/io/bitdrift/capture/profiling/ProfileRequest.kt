// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

import android.os.Build
import android.os.ProfilingResult
import androidx.annotation.RequiresApi

internal enum class ProfileType {
    STACK_SAMPLING,
}

/** One capture as the platform should run it; the core has already validated every value. */
internal data class ProfileRequest(
    val requestId: Long,
    val type: ProfileType,
    val durationMs: Long,
    val frequencyHz: Int,
    val bufferSizeKb: Int,
    /** Marks the capture as the SDK's; the platform puts it in the file name and in the result. */
    val tag: String,
)

internal sealed interface ProfileOutcome {
    data class Saved(
        val path: String,
    ) : ProfileOutcome

    /** [code] and [name] are the profiler's own error, or 0 and an SDK name for earlier failures. */
    data class Failed(
        val code: Int,
        val name: String,
        val message: String,
    ) : ProfileOutcome
}

/** The ProfilingResult.ERROR_* constant for a code, without the redundant prefix. */
internal object ProfilingErrorCode {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun name(code: Int): String =
        when (code) {
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
