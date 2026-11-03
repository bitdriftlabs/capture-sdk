// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

/** Stops a running capture early. */
internal fun interface ICancellable {
    fun cancel()
}

/** The platform profiler. Only its implementation touches android.os.ProfilingManager. */
internal interface IProfiler {
    /** Starts [request] and reports its outcome exactly once through [callback]. */
    fun request(
        request: ProfileRequest,
        callback: (ProfileOutcome) -> Unit,
    ): ICancellable
}
