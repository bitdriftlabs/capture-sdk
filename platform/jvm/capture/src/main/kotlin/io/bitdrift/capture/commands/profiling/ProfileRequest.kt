// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

/** One capture as requested, with the settings its type supports; the platform clamps each value to its own bounds. */
internal sealed interface ProfileRequest {
    /** Marks the capture as the SDK's; the platform puts it in the file name and in the result. */
    val tag: String

    data class StackSampling(
        val durationMs: Int,
        val frequencyHz: Int,
        val bufferSizeKb: Int,
        override val tag: String,
    ) : ProfileRequest

    data class HeapProfile(
        val durationMs: Int,
        val bufferSizeKb: Int,
        val samplingIntervalBytes: Long,
        /** Records Java heap allocations instead of native ones; a profile cannot record both. */
        val trackJavaAllocations: Boolean,
        override val tag: String,
    ) : ProfileRequest
}

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
