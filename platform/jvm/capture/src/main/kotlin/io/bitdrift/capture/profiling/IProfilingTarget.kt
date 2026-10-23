// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.profiling

/**
 * The platform side of the built-in stack sampling command. The Rust core calls these on its own
 * runtime thread; every [captureStackSample] must end in exactly one
 * [IProfilingBridge.completeStackSample] for its request ID, including after a cancel.
 */
interface IProfilingTarget {
    /**
     * Records a stack sampling trace of this process and reports its file through the bridge.
     * [tag] is recorded with the capture and exposed to the app through `ProfilingResult.getTag()`.
     */
    fun captureStackSample(
        requestId: Long,
        durationMs: Long,
        frequencyHz: Int,
        bufferSizeKb: Int,
        tag: String,
    )

    /** Asks a running capture to stop early. Its completion is still reported, and ignored. */
    fun cancelStackSample(requestId: Long)
}

/** Used below Android 15, where there is no ProfilingManager: every capture fails as unsupported. */
internal class NoopProfilingTarget(
    private val bridge: IProfilingBridge,
) : IProfilingTarget {
    override fun captureStackSample(
        requestId: Long,
        durationMs: Long,
        frequencyHz: Int,
        bufferSizeKb: Int,
        tag: String,
    ) {
        bridge.completeStackSample(
            requestId,
            null,
            0,
            UNSUPPORTED_ERROR_NAME,
            "stack sampling requires Android 15 or newer",
        )
    }

    override fun cancelStackSample(requestId: Long) = Unit

    companion object {
        internal const val UNSUPPORTED_ERROR_NAME = "unsupported"
    }
}
