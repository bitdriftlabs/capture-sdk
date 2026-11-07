// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

import io.bitdrift.capture.commands.BuiltinCommandArguments

/** Stops a running capture early. */
internal fun interface ICancellable {
    fun cancel()
}

/** The platform profiler. Only its implementation touches android.os.ProfilingManager. */
internal interface IProfiler {
    /** Starts the capture [arguments] describe and reports its outcome exactly once through [callback]. */
    fun request(
        arguments: BuiltinCommandArguments,
        callback: (ProfileOutcome) -> Unit,
    ): ICancellable
}
