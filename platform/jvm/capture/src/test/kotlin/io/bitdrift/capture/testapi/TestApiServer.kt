// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.testapi

import io.bitdrift.capture.CaptureTestJniLibrary
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.UploadedLog
import io.bitdrift.capture.providers.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A test API server for integration testing with automatic lifecycle management.
 *
 * Use [TestApiServer.start] to create and automatically manage the lifecycle:
 *
 * ```kotlin
 * TestApiServer.start { server ->
 *     // Server automatically started
 *     val stream = server.awaitNextStream()
 *     stream.configureAggressiveUploads()
 *
 *     logger.log(LogLevel.DEBUG) { "test" }
 *
 *     val logs = server.collectLogs(2)
 *     assertThat(logs[0].message).isEqualTo("SDKConfigured")
 * } // Server automatically stopped
 * ```
 */
class TestApiServer private constructor(
    val port: ServerPort,
    val config: ServerConfig,
) : AutoCloseable {
    companion object {
        /**
         * Starts a test server with automatic lifecycle management.
         * The server is automatically stopped when the block completes, even if an exception is thrown.
         *
         * @param config Server configuration (ping interval, etc.)
         * @param block Code to execute with the server
         * @return The result of the block
         */
        suspend inline fun <R> start(
            config: ServerConfig = ServerConfig(),
            crossinline block: suspend TestApiServer.() -> R,
        ): R {
            val pingIntervalMs =
                if (config.pingInterval.isInfinite()) {
                    -1
                } else {
                    config.pingInterval.inWholeMilliseconds.toInt()
                }

            val port = ServerPort(CaptureTestJniLibrary.startTestApiServer(pingIntervalMs))
            val server = TestApiServer(port, config)

            return try {
                server.block()
            } finally {
                // Always cleanup, even if the coroutine is cancelled
                withContext(NonCancellable) {
                    server.close()
                }
            }
        }

        /**
         * Starts a test server with DSL-style configuration.
         *
         * Example:
         * ```kotlin
         * TestApiServer.start(configure = {
         *     pingInterval = 100.milliseconds
         * }) { server ->
         *     // ...
         * }
         * ```
         */
        suspend inline fun <R> start(
            crossinline configure: ServerConfig.Builder.() -> Unit = {},
            crossinline block: suspend TestApiServer.() -> R,
        ): R = start(ServerConfig.build(configure), block)
    }

    /**
     * The base URL for this test server.
     */
    val url: String get() = "https://localhost:${port.value}"

    /**
     * Waits for the next API stream to be established.
     * This is a suspending function that blocks until a stream connects or times out.
     *
     * @param timeout Maximum time to wait for a stream (not currently enforced by underlying API)
     * @return The newly established stream
     * @throws IllegalStateException if stream establishment fails
     */
    suspend fun awaitNextStream(timeout: Duration = Duration.INFINITE): TestApiStream =
        withContext(Dispatchers.IO) {
            val streamId = StreamId(CaptureTestJniLibrary.awaitNextApiStream())
            require(streamId.isValid) { "Failed to establish stream (got invalid stream ID: ${streamId.value})" }
            TestApiStream(streamId)
        }

    /**
     * Returns the next uploaded log.
     * Blocks until a log is available (up to 5 seconds internally).
     *
     * @return The next uploaded log
     */
    suspend fun nextUploadedLog(): UploadedLog =
        withContext(Dispatchers.IO) {
            CaptureTestJniLibrary.nextUploadedLog()
        }

    /**
     * Collects the next N uploaded logs.
     * Useful for consuming multiple logs at once.
     *
     * @param count Number of logs to collect
     * @return List of collected logs
     */
    suspend fun collectLogs(count: Int): List<UploadedLog> = (1..count).map { nextUploadedLog() }

    /**
     * Collects uploaded logs until a condition is met.
     * Useful for waiting for a specific log.
     *
     * @param maxLogs Maximum number of logs to collect (safety limit)
     * @param predicate Condition that, when true, stops collection
     * @return List of collected logs (including the one that matched the predicate)
     */
    suspend fun collectLogsUntil(
        maxLogs: Int = 100,
        predicate: (UploadedLog) -> Boolean,
    ): List<UploadedLog> {
        val logs = mutableListOf<UploadedLog>()
        repeat(maxLogs) {
            val log = nextUploadedLog()
            logs.add(log)
            if (predicate(log)) return logs
        }
        error("Predicate not satisfied after collecting $maxLogs logs")
    }

    override fun close() {
        CaptureTestJniLibrary.stopTestApiServer()
    }
}

/**
 * DSL for building test scenarios with streams.
 * Automatically waits for a stream and executes the block with it.
 *
 * Example:
 * ```kotlin
 * TestApiServer.start { server ->
 *     server.withStream { stream ->
 *         stream.configureAggressiveUploads()
 *         // ... test code
 *     }
 * }
 * ```
 */
suspend inline fun <R> TestApiServer.withStream(crossinline block: suspend TestApiStream.() -> R): R {
    val stream = awaitNextStream()
    return stream.block()
}

/**
 * DSL for testing multiple streams.
 * Waits for N streams to connect and executes the block with all of them.
 *
 * Example:
 * ```kotlin
 * server.withStreams(count = 2) { streams ->
 *     streams[0].configureAggressiveUploads()
 *     streams[1].configureAggressiveUploads()
 *     // ... test code
 * }
 * ```
 */
suspend inline fun <R> TestApiServer.withStreams(
    count: Int,
    crossinline block: suspend (List<TestApiStream>) -> R,
): R {
    val streams = (1..count).map { awaitNextStream() }
    return block(streams)
}

// ============================================================================
// UploadedLog Extensions
// ============================================================================

/**
 * Checks if this log contains all specified field keys.
 */
fun UploadedLog.hasFields(vararg keys: String): Boolean = keys.all { it in fields }

/**
 * Gets a field value with the specified key.
 */
fun UploadedLog.getField(key: String): FieldValue? = fields[key]

/**
 * Gets a string field value, or null if not present or not a string.
 */
fun UploadedLog.getStringField(key: String): String? =
    when (val value = fields[key]) {
        is FieldValue.StringField -> value.value
        else -> null
    }

/**
 * Asserts that this log matches expected values.
 * Only checks non-null parameters.
 *
 * Example:
 * ```kotlin
 * log.assertMatches(
 *     level = LogLevel.DEBUG,
 *     message = "test log",
 *     requiredFields = setOf("foo", "bar")
 * )
 * ```
 */
fun UploadedLog.assertMatches(
    level: LogLevel? = null,
    message: String? = null,
    requiredFields: Set<String>? = null,
) {
    level?.let {
        check(this.level == it.value) {
            "Expected log level ${it.name} (${it.value}), but got ${LogLevel.fromInt(this.level)?.name} (${this.level})"
        }
    }
    message?.let {
        check(this.message == it) {
            "Expected message '$it', but got '${this.message}'"
        }
    }
    requiredFields?.let { required ->
        val missing = required - fields.keys
        check(missing.isEmpty()) {
            "Missing required fields: $missing. Available fields: ${fields.keys}"
        }
    }
}

/**
 * Asserts that this log has the specified message.
 * Convenience method for the most common assertion.
 */
fun UploadedLog.assertMessage(expected: String) {
    check(message == expected) {
        "Expected message '$expected', but got '$message'"
    }
}

/**
 * Asserts that this log has the specified level.
 */
fun UploadedLog.assertLevel(expected: LogLevel) {
    check(level == expected.value) {
        "Expected level ${expected.name}, but got ${LogLevel.fromInt(level)?.name}"
    }
}
