// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

/** The core's completion entry point for stack samples, implemented by the JNI library. */
internal interface IProfilingBridge {
    /**
     * @param path the recorded trace on success, or null on failure.
     * @param errorCode the platform profiler's error code, or 0 when the failure is the SDK's own.
     * @param errorName a short name for the failure, such as the ProfilingResult.ERROR_* constant.
     * @param errorMessage a human readable description of the failure.
     */
    fun completeStackSample(
        requestId: Long,
        path: String?,
        errorCode: Int,
        errorName: String?,
        errorMessage: String?,
    )
}
