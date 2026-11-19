// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.session.SessionConfiguration
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.webview.WebViewBridgeMessageHandler
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.management.ManagementFactory
import java.util.UUID
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val LOG_MESSAGE = "50 characters long test message - 0123456789012345"

/**
 * Pins the Java heap bytes each SDK operation allocates on the calling thread, for the operations
 * the Android microbenchmarks time.
 *
 * Each scenario warms up, then runs [ROUNDS] rounds and records the calling thread's allocated
 * bytes per round. Background SDK executors keep their own threads, so their work is excluded, as
 * are native (Rust) allocations. Budgets are exact host-JVM byte counts, so they depend on the
 * JDK's object layout (Bazel's hermetic JDK 17) rather than ART's. When a change legitimately
 * alters an operation's allocations, update its budget to the measured value in the failure
 * message. Results also go to stdout and to `alloc_counts.txt` in the test's undeclared outputs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AllocationCountTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val results = mutableListOf<String>()
    private val failures = mutableListOf<String>()

    @Before
    fun setUp() {
        ContextHolder().create(ApplicationProvider.getApplicationContext())
        CaptureJniLibrary.load()
    }

    @After
    fun tearDown() {
        (Capture.logger() as? LoggerImpl)?.let {
            CaptureJniLibrary.shutdown(it.loggerId)
            CaptureJniLibrary.destroyLogger(it.loggerId)
        }
        Capture.Logger.resetShared()

        val outputDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR") ?: return
        File(outputDir, "alloc_counts.txt").appendText(results.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun loggerOperations() {
        startLogger(createInitialFields())
        assertThat(Capture.logger()).describedAs("logger started").isInstanceOf(LoggerImpl::class.java)

        measure("logNotMatchedNoFields", budgetBytesPerCall = 0) { Capture.Logger.logInfo { LOG_MESSAGE } }
        // Field count to the bytes one call allocates on the calling thread.
        val fieldBudgets = mapOf(1 to 232L, 10 to 360L, 50 to 1_000L, 100 to 1_800L, 1_000 to 16_200L, 5_000 to 80_200L)
        for ((count, budget) in fieldBudgets) {
            val fields = buildFieldsMap(count)
            val iterations = if (count >= 1000) 100 else ITERATIONS
            measure("logNotMatched${count}Fields", budget, iterations, minWarmupIterations = 2 * iterations) {
                Capture.Logger.logInfo(fields) { LOG_MESSAGE }
            }
        }

        // Spans and HTTP logs take a UUID per call. The SDK generates a random one by default, and the
        // JDK's random source allocates differently by platform, so pass sequential IDs instead.
        // Spans are built the way LoggerImpl.startSpan builds them, with an explicit ID.
        val logger = Capture.logger() as IInternalLogger
        measure("trackSpansWithoutFields", budgetBytesPerCall = 776) {
            Span(logger, "Span without metadata", LogLevel.INFO, id = nextId()).end(SpanResult.SUCCESS)
        }
        val metadata = buildFieldsMap(500)
        measure("trackSpansWithFields", budgetBytesPerCall = 19_736) {
            Span(logger, "Span with metadata", LogLevel.INFO, metadata.toFields(), id = nextId()).end(SpanResult.SUCCESS)
        }

        val extraFields = buildFieldsMap(50)
        val headers = buildFieldsMap(50, keyIdentifier = "header_")
        measure("logHttpNetworkLog50FieldsAndHeadersAndInitialFields", budgetBytesPerCall = 48_544) {
            val request =
                HttpRequestInfo(
                    method = "GET",
                    host = "www.google.com",
                    path = HttpUrlPath(value = "/search", template = "/search"),
                    query = "q=Bitdrift",
                    headers = headers,
                    spanId = nextId(),
                    extraFields = extraFields,
                )
            Capture.Logger.log(request)
            val response =
                HttpResponse(
                    host = request.host,
                    path = HttpUrlPath(request.path?.value ?: "/"),
                    query = request.query,
                    statusCode = 200,
                    result = HttpResponse.HttpResult.SUCCESS,
                    headers = headers,
                )
            Capture.Logger.log(
                HttpResponseInfo(request = request, response = response, durationMs = 100, extraFields = extraFields),
            )
        }

        val handler = WebViewBridgeMessageHandler(logger, "automatic_full")
        // Each invalid message builds an exception, so keep this one short. The warmup also uses up
        // the native error reporter's first few reports, which take a different path.
        measure("webViewBridgeInvalidJson", budgetBytesPerCall = 9408, iterations = 100, minWarmupIterations = 200) {
            handler.log("invalid json {")
        }
        val bridgeReady =
            """{"v":1,"type":"bridgeReady","url":"https://example.com",""" +
                """"instrumentationConfig":{"capturePageViews":true,"captureErrors":false}}"""
        measure("webViewBridgeBridgeReady", budgetBytesPerCall = 5408) { handler.log(bridgeReady) }

        measure("startNewSession", budgetBytesPerCall = 0) { Capture.Logger.startNewSession() }
        measure("getSdkStatus", budgetBytesPerCall = 24) { Capture.Logger.getSdkStatus() }
        measure("logAppLaunchTTI", budgetBytesPerCall = 0) { Capture.Logger.logAppLaunchTTI(1.toDuration(DurationUnit.SECONDS)) }
        measure("setEntityId", budgetBytesPerCall = 0) { Capture.Logger.setEntityId("fake_id") }
        measure("clearEntityId", budgetBytesPerCall = 0) { Capture.Logger.clearEntityId() }

        assertThat(failures).describedAs("allocation budgets").isEmpty()
    }

    /**
     * Fails the test unless the cheapest of [ROUNDS] rounds allocates exactly [budgetBytesPerCall]
     * bytes per call. Using the cheapest round tolerates a one-time allocation in a single round,
     * while any new per-call allocation raises every round and still fails.
     */
    private fun measure(
        name: String,
        budgetBytesPerCall: Long,
        iterations: Int = ITERATIONS,
        minWarmupIterations: Int = MIN_WARMUP_ITERATIONS,
        block: () -> Unit,
    ) {
        val rounds = sample(name, iterations, minWarmupIterations, block)
        if (rounds.min() != budgetBytesPerCall * iterations) {
            failures += "$name: expected $budgetBytesPerCall bytes per call, measured ${rounds.min().toDouble() / iterations}"
        }
    }

    private fun sample(
        name: String,
        iterations: Int,
        minWarmupIterations: Int,
        block: () -> Unit,
    ): LongArray {
        repeat(maxOf(2 * iterations, minWarmupIterations)) { block() }

        val rounds =
            LongArray(ROUNDS) {
                val before = threadBean.currentThreadAllocatedBytes
                repeat(iterations) { block() }
                threadBean.currentThreadAllocatedBytes - before
            }

        val line = "name=$name iterations=$iterations bytes=${rounds.joinToString(",")}"
        println("AllocCount $line")
        results += line
        return rounds
    }

    /** A new, deterministic UUID per call, so the IDs the SDK requires don't need a random source. */
    private fun nextId(): UUID = UUID(0L, nextIdValue++)

    private var nextIdValue = 0L

    private fun startLogger(initialFields: Map<String, String>) {
        Capture.Logger.start(
            apiKey = "[test_api_key]",
            apiUrl = "https://localhost:1234".toHttpUrl(),
            context = ApplicationProvider.getApplicationContext(),
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration(initialSessionId = "SESSION_ID")),
            initialFields = initialFields,
        )
    }

    private fun createInitialFields(
        providers: Int = 5,
        fields: Int = 10,
    ): Map<String, String> =
        (1..providers)
            .flatMap { providerIndex ->
                (1..fields).map { fieldIndex ->
                    "provider${providerIndex}_key$fieldIndex" to "provider${providerIndex}_val$fieldIndex"
                }
            }.toMap()

    private fun buildFieldsMap(
        size: Int,
        keyIdentifier: String = "key_",
    ): Map<String, String> = (1..size).associate { "$keyIdentifier$it" to "value_$it" }

    private companion object {
        const val ITERATIONS = 1_000
        const val MIN_WARMUP_ITERATIONS = 2_000
        const val ROUNDS = 5
    }
}
