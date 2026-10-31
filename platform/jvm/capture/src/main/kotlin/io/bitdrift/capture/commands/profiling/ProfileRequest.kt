// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

internal enum class ProfileType {
    STACK_SAMPLING,
}

/** One capture as the platform should run it; the command has already validated every value. */
internal data class ProfileRequest(
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
