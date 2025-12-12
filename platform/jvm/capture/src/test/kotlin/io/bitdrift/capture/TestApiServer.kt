// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import okhttp3.HttpUrl
import java.nio.ByteBuffer

/**
 * Represents a handle to an API stream for performing operations on that stream.
 */
class StreamHandle(
    private val streamId: Int,
) {
    /**
     * Configures this stream with aggressive continuous uploads.
     * This enables fast log and artifact uploads for testing.
     * Also enables crash reporting and persistent storage by default.
     */
    fun configureAggressiveUploads() {
        CaptureTestJniLibrary.configureAggressiveContinuousUploads(streamId)
    }

    /**
     * Waits for and returns the next handshake on this stream.
     *
     * @param metadata optional metadata to verify
     * @param metadataKeysToIgnore optional keys to ignore in metadata verification
     * @return true if handshake was received, false otherwise
     */
    fun awaitHandshake(
        metadata: Map<String, String>? = null,
        metadataKeysToIgnore: List<String>? = null,
    ): Boolean = CaptureTestJniLibrary.awaitApiServerReceivedHandshake(streamId, metadata, metadataKeysToIgnore)

    /**
     * Sends a configuration update on this stream.
     */
    fun sendConfigurationUpdate() {
        CaptureTestJniLibrary.sendConfigurationUpdate(streamId)
    }

    /**
     * Waits for a configuration acknowledgment on this stream.
     *
     * @param waitTimeMs maximum time to wait in milliseconds
     * @return true if ack was received, false otherwise
     */
    fun awaitConfigurationAck(waitTimeMs: Int = 5000): Boolean = CaptureTestJniLibrary.awaitConfigurationAck(streamId, waitTimeMs)

    /**
     * Waits for this stream to close.
     *
     * @param waitTimeMs maximum time to wait in milliseconds
     * @return true if stream closed, false otherwise
     */
    fun awaitStreamClosed(waitTimeMs: Int = 5000): Boolean = CaptureTestJniLibrary.awaitApiServerStreamClosed(streamId, waitTimeMs)

    /**
     * Disables a runtime feature on this stream.
     *
     * @param feature the feature flag path to disable
     */
    fun disableRuntimeFeature(feature: String) {
        CaptureTestJniLibrary.disableRuntimeFeature(streamId, feature)
    }
}

/**
 * A wrapper around the test API server that provides better ergonomics for tests.
 * Handles server lifecycle, port management, and stream management automatically.
 *
 * Usage:
 * ```
 * @Before
 * fun setUp() {
 *     testServer = TestApiServer()
 * }
 *
 * @After
 * fun tearDown() {
 *     testServer.stop()
 * }
 *
 * @Test
 * fun myTest() {
 *     val logger = LoggerImpl(apiUrl = testServer.url, ...)
 *     val stream = testServer.awaitNextStream()
 *     stream.configureAggressiveUploads()
 *     val log = testServer.nextUploadedLog()
 *     // ...
 * }
 * ```
 */
class TestApiServer {
    private val port: Int

    init {
        port = CaptureTestJniLibrary.startTestApiServer(-1)
    }

    /**
     * The HTTP URL for connecting to this test server.
     */
    val url: HttpUrl
        get() =
            HttpUrl
                .Builder()
                .scheme("http")
                .host("localhost")
                .port(port)
                .build()

    /**
     * Stops the test server. Should be called in @After.
     */
    fun stop() {
        CaptureTestJniLibrary.stopTestApiServer()
    }

    /**
     * Waits for and returns a handle to the next API stream. This must be called after
     * logger initialization to obtain the stream handle.
     *
     * @return a StreamHandle for performing operations on the stream
     * @throws AssertionError if no stream is established
     */
    fun awaitNextStream(): StreamHandle {
        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        if (streamId == -1) {
            throw AssertionError("Failed to await next API stream")
        }
        return StreamHandle(streamId)
    }

    /**
     * Returns the next uploaded log, blocking until one is available.
     */
    fun nextUploadedLog(): UploadedLog = CaptureTestJniLibrary.nextUploadedLog()

    /**
     * Returns the next uploaded artifact with its feature flags, blocking until one is available.
     */
    fun nextUploadedArtifact(): UploadedArtifact = CaptureTestJniLibrary.nextUploadedArtifact()

    /**
     * Returns the next uploaded artifact decoded as a Report, blocking until one is available.
     * Also returns the feature flags from the upload request.
     */
    fun nextUploadedReport(): Pair<Report, Map<String, String?>> {
        val artifact = nextUploadedArtifact()
        val buffer = ByteBuffer.wrap(artifact.contents)
        return Pair(Report.getRootAsReport(buffer), artifact.featureFlags)
    }
}
