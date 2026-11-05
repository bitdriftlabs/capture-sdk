// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

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
