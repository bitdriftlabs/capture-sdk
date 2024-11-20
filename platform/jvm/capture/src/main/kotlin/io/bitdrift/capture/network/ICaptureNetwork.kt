// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

/**
 * An interface describing a single active stream, which is used by the native api task to transmit
 * data up to the Capture backend.
 */
interface ICaptureStream {
    /**
     * Called when the Capture library wants to send data over the active stream.
     * If there is no active stream, this does nothing.
     */
    fun sendData(dataToSend: ByteArray)

    /**
     * Called to tear down the stream. This is called whenever the native end is done with the stream,
     * so this function should gracefully handle receiving this when the stream has shut down through
     * other means.
     */
    fun shutdown()
}

/**
 * An interface describing the top-level entry point for API stream management, allowing the native
 * api task to initiate new streams to the Bitdrift backend.
 */
interface ICaptureNetwork {

    /**
     * Called when the Capture library intends to initialize a new stream. For each startStream call received,
     * the receiver *must* call Jni.releaseApiStream exactly once with the provided streamId in order to
     * properly deallocate native memory.
     *
     * @param streamId an opaque id that identifies the new stream.
     * @param headers headers to add to network requests.
     * @return a new stream handle
     */
    fun startStream(streamId: Long, headers: Map<String, String>): ICaptureStream

    /**
     * Called to tear down the underlying networking client.
     */
    fun shutdown()
}

internal object Jni {
    /**
     * Called to provide the native API implementation with data for processing.
     */
    external fun onApiChunkReceived(streamId: Long, dataToSend: ByteArray, size: Int)

    /**
     * Called to notify the native API implementation that the stream has closed.
     */
    external fun onApiStreamClosed(streamId: Long, reason: String)

    /**
     * Releases the native memory associated with the stream. This must be called for each stream
     * returned by startStream to prevent a memory leak, and may not be called more than once.
     * @param streamId which identifies the stream to release.
     */
    external fun releaseApiStream(streamId: Long)
}
