// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.reports.IFatalIssueReporter
import io.bitdrift.capture.testapi.*
import io.bitdrift.capture.threading.CaptureDispatchers
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

// This should return "2022-07-05T18:55:58.123Z" when formatted.
private const val TEST_DATE_TIMESTAMP: Long = 1657047358123

/**
 * Demonstrates the new idiomatic Kotlin test API.
 *
 * This test class shows how to use the new [TestApiServer] with:
 * - Automatic resource management (no manual cleanup)
 * - Kotlin coroutines (suspend functions)
 * - Type-safe stream IDs and ports
 * - Fluent DSL for common operations
 * - Better assertion helpers
 *
 * Compare with [CaptureLoggerTest] to see the improvements.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class CaptureLoggerTestNew {
    private val systemDateProvider =
        DateProvider {
            Date(TEST_DATE_TIMESTAMP)
        }

    private lateinit var logger: LoggerImpl
    private val fatalIssueReporter: IFatalIssueReporter = FatalIssueReporter(dateProvider = FakeDateProvider)

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        CaptureDispatchers.setTestExecutorService(MoreExecutors.newDirectExecutorService())
        CaptureJniLibrary.load()
    }

    /**
     * This is the new idiomatic way to write tests with the test API.
     *
     * Key improvements over the old API:
     * 1. ✅ Automatic server cleanup (no @After needed)
     * 2. ✅ Kotlin coroutines (natural async/await)
     * 3. ✅ Type-safe stream IDs (can't mix up integers)
     * 4. ✅ Fluent DSL (stream.configureAggressiveUploads())
     * 5. ✅ Better assertions (log.assertMessage(), log.assertLevel())
     * 6. ✅ Composable (collectLogsUntil, withStream, etc.)
     */
    @Test
    @Config(qualifiers = "+ar") // test arabic date parsing
    fun test_logger_end_to_end_new_api() =
        runTest {
            // Server automatically started and stopped
            TestApiServer.start { server ->
                // Build logger pointing to test server
                logger =
                    buildLogger(
                        dateProvider = systemDateProvider,
                        apiBaseURL = server.url,
                    )

                // Use DSL to wait for stream and configure it
                server.withStream { stream ->
                    stream.configureAggressiveUploads()

                    // Log something
                    logger.log(LogLevel.DEBUG, fields = mapOf("fields" to "passed_in")) { "test log" }

                    // Collect logs with helper - automatically skips SDK internal logs
                    val logs =
                        server.collectLogsUntil(maxLogs = 10) { log ->
                            log.message == "test log"
                        }

                    // Find the test log (last one collected)
                    val testLog = logs.last()

                    // Use improved assertion helpers
                    testLog.assertLevel(LogLevel.DEBUG)
                    testLog.assertMessage("test log")

                    // Traditional assertions still work
                    val expectedFields =
                        mapOf(
                            "fields" to "passed_in".toFieldValue(),
                        ) + getDefaultFields()

                    assertThat(testLog.fields).isEqualTo(expectedFields)
                    assertThat(testLog.sessionId).isEqualTo("SESSION_ID")
                    assertThat(testLog.rfc3339Timestamp).isEqualTo("2022-07-05T18:55:58.123Z")

                    // Verify SDK configured log was sent
                    val sdkConfiguredLog = logs.find { it.message == "SDKConfigured" }
                    assertThat(sdkConfiguredLog).isNotNull
                }
            } // Server automatically stopped here
        }

    /**
     * Alternative style: more explicit control over logs.
     * Shows how to handle SDK internal logs deterministically.
     */
    @Test
    fun test_global_fields_new_api() =
        runTest {
            TestApiServer.start { server ->
                logger =
                    buildLogger(
                        dateProvider = systemDateProvider,
                        apiBaseURL = server.url,
                    )

                server.withStream { stream ->
                    stream.configureAggressiveUploads()

                    // Add valid field, it should be present.
                    logger.addField("foo", "value_foo")

                    // Add and override field, only its latest value should be present.
                    logger.addField("bar", "value_to_override")
                    logger.addField("bar", "value_bar")

                    // Add and remove field, it should not be present in the end.
                    logger.addField("car", "value_car")
                    logger.removeField("car")

                    // Add field with an invalid key as its name starts with "_".
                    logger.addField("_dar", "value_dar")

                    logger.log(LogLevel.DEBUG, fields = mapOf("fields" to "passed_in")) { "test log" }

                    val expectedFields =
                        mapOf(
                            "bar" to "value_bar".toFieldValue(),
                            "fields" to "passed_in".toFieldValue(),
                            "foo" to "value_foo".toFieldValue(),
                        ) + getDefaultFields()

                    // Collect logs explicitly
                    val logs = server.collectLogs(4)

                    // Verify logs in order
                    logs[0].assertMessage("SDKConfigured")
                    logs[1].assertMessage("") // Resource log
                    logs[2].assertMessage("AppMemPressure")

                    val testLog = logs[3]
                    testLog.assertLevel(LogLevel.DEBUG)
                    testLog.assertMessage("test log")
                    assertThat(testLog.fields).isEqualTo(expectedFields)
                    assertThat(testLog.sessionId).isEqualTo("SESSION_ID")
                    assertThat(testLog.rfc3339Timestamp).isEqualTo("2022-07-05T18:55:58.123Z")
                }
            }
        }

    /**
     * Shows multiple streams in a single test.
     * Demonstrates the composability of the new API.
     */
    @Test
    fun test_multiple_streams_new_api() =
        runTest {
            TestApiServer.start { server ->
                logger =
                    buildLogger(
                        dateProvider = systemDateProvider,
                        apiBaseURL = server.url,
                    )

                // First stream
                server.withStream { stream1 ->
                    stream1.configureAggressiveUploads()
                    logger.log(LogLevel.INFO) { "from stream 1" }

                    val log = server.collectLogsUntil { it.message == "from stream 1" }.last()
                    log.assertMessage("from stream 1")

                    // Close the stream (SDK will reconnect)
                    // In practice you'd trigger this via SDK behavior
                }

                // Second stream (after reconnect)
                server.withStream { stream2 ->
                    stream2.configureAggressiveUploads()
                    logger.log(LogLevel.INFO) { "from stream 2" }

                    val log = server.collectLogsUntil { it.message == "from stream 2" }.last()
                    log.assertMessage("from stream 2")
                }
            }
        }

    // Helper method from original test class
    private fun getDefaultFields() =
        mapOf(
            "_build_type" to "debug".toFieldValue(),
        )

    private fun buildLogger(
        dateProvider: DateProvider = systemDateProvider,
        apiBaseURL: String,
    ): LoggerImpl {
        // Implementation would be similar to original buildLogger
        // but using the test server URL
        TODO("Implement buildLogger with test server URL")
    }
}
