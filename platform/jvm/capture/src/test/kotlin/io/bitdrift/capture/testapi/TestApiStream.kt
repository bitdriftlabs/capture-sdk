// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.testapi

import io.bitdrift.capture.CaptureTestJniLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents an active API stream with scoped operations.
 * Provides a fluent interface for stream-related test operations.
 *
 * Example:
 * ```
 * val stream = server.awaitNextStream()
 * stream.configureAggressiveUploads()
 *       .sendConfigurationUpdate()
 *       .awaitConfigurationAck()
 * ```
 */
class TestApiStream internal constructor(
    val id: StreamId,
) {
    /**
     * Waits for the handshake to be received with optional metadata validation.
     *
     * @param expectedMetadata Optional metadata to validate against the handshake
     * @param ignoreKeys Keys to ignore when validating metadata
     * @param timeout Maximum time to wait for handshake
     * @return Result indicating success or failure details
     */
    suspend fun awaitHandshake(
        expectedMetadata: Map<String, String>? = null,
        ignoreKeys: Set<String> = emptySet(),
        timeout: Duration = 15.seconds,
    ): HandshakeResult =
        withContext(Dispatchers.IO) {
            val success =
                CaptureTestJniLibrary.awaitApiServerReceivedHandshake(
                    id.value,
                    expectedMetadata,
                    ignoreKeys.toList(),
                )
            if (success) {
                HandshakeResult.Success
            } else {
                HandshakeResult.Timeout(timeout)
            }
        }

    /**
     * Asserts that the handshake is received successfully.
     * Throws an exception with detailed error message if handshake fails.
     *
     * @param expectedMetadata Optional metadata to validate
     * @param ignoreKeys Keys to ignore during validation
     * @param timeout Maximum time to wait
     */
    suspend fun assertHandshakeReceived(
        expectedMetadata: Map<String, String>? = null,
        ignoreKeys: Set<String> = emptySet(),
        timeout: Duration = 15.seconds,
    ) {
        val result = awaitHandshake(expectedMetadata, ignoreKeys, timeout)
        check(result is HandshakeResult.Success) {
            when (result) {
                is HandshakeResult.Success -> ""
                is HandshakeResult.Timeout -> "Handshake timed out after $timeout"
                is HandshakeResult.MetadataMismatch ->
                    """
                    Handshake metadata mismatch:
                    Expected: ${result.expected}
                    Actual: ${result.actual}
                    Differences: ${result.differences}
                    """.trimIndent()
            }
        }
    }

    /**
     * Configures aggressive continuous uploads for testing purposes.
     * This makes logs upload immediately for easier testing.
     *
     * @return This stream for chaining
     */
    fun configureAggressiveUploads(): TestApiStream =
        apply {
            CaptureTestJniLibrary.configureAggressiveContinuousUploads(id.value)
        }

    /**
     * Sends a configuration update to this stream.
     *
     * @return This stream for chaining
     */
    fun sendConfigurationUpdate(): TestApiStream =
        apply {
            CaptureTestJniLibrary.sendConfigurationUpdate(id.value)
        }

    /**
     * Waits for configuration acknowledgment from the SDK.
     *
     * @param timeout Maximum time to wait
     * @return Result indicating success or timeout
     */
    suspend fun awaitConfigurationAck(timeout: Duration = 5.seconds): ConfigurationAckResult =
        withContext(Dispatchers.IO) {
            val success =
                CaptureTestJniLibrary.awaitConfigurationAck(
                    id.value,
                    timeout.inWholeMilliseconds.toInt(),
                )
            if (success) {
                ConfigurationAckResult.Success
            } else {
                ConfigurationAckResult.Timeout(timeout)
            }
        }

    /**
     * Asserts that configuration acknowledgment is received.
     *
     * @param timeout Maximum time to wait
     */
    suspend fun assertConfigurationAcked(timeout: Duration = 5.seconds) {
        val result = awaitConfigurationAck(timeout)
        check(result is ConfigurationAckResult.Success) {
            "Configuration ack timed out after $timeout"
        }
    }

    /**
     * Waits for this stream to close.
     *
     * @param timeout Maximum time to wait
     * @return Result indicating whether stream closed or timed out
     */
    suspend fun awaitClosed(timeout: Duration = 5.seconds): StreamCloseResult =
        withContext(Dispatchers.IO) {
            val closed =
                CaptureTestJniLibrary.awaitApiServerStreamClosed(
                    id.value,
                    timeout.inWholeMilliseconds.toInt(),
                )
            if (closed) {
                StreamCloseResult.Closed
            } else {
                StreamCloseResult.StillOpen
            }
        }

    /**
     * Asserts that the stream closes within the timeout.
     *
     * @param timeout Maximum time to wait
     */
    suspend fun assertClosed(timeout: Duration = 5.seconds) {
        val result = awaitClosed(timeout)
        check(result is StreamCloseResult.Closed) {
            "Stream did not close within $timeout"
        }
    }

    /**
     * Disables a runtime feature for this stream.
     *
     * @param feature The feature name to disable
     * @return This stream for chaining
     */
    fun disableFeature(feature: String): TestApiStream =
        apply {
            CaptureTestJniLibrary.disableRuntimeFeature(id.value, feature)
        }

    override fun toString(): String = "TestApiStream(id=${id.value})"
}
