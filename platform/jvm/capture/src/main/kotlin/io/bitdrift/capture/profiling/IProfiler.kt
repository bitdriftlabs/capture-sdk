// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

/** Stops a running capture early. */
internal fun interface IProfilingCancel {
    fun cancel()
}

/** The platform profiler. Only its implementation touches android.os.ProfilingManager. */
internal interface IProfiler {
    /** Starts [request] and reports its outcome exactly once through [callback]. */
    fun request(
        request: ProfileRequest,
        callback: (ProfileOutcome) -> Unit,
    ): IProfilingCancel
}
