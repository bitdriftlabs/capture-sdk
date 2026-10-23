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

/** One capture as the platform should run it; the core has already clamped every value. */
internal data class ProfileRequest(
    val requestId: Long,
    val type: ProfileType,
    val durationMs: Long,
    val frequencyHz: Int,
    val bufferSizeKb: Int,
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

internal object ProfilingErrorCode {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun name(code: Int): String =
        when (code) {
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
}
