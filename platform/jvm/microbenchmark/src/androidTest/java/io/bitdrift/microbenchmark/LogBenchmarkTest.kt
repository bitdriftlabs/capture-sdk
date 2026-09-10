// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.bitdrift.capture.Capture
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.test.support.fakes.FakeBridge
import io.bitdrift.capture.test.support.fakes.FakeRuntime
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponse.HttpResult
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.webview.WebViewBridgeMessageHandler
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val LOG_MESSAGE = "50 characters long test message - 0123456789012345"

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop.
 * In microbenchmarks the number of iterations to be executed is determined by the library itself
 */
@RunWith(AndroidJUnit4::class)
class LogBenchmarkTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private fun startLogger(
        initialFields: Map<String, String> = emptyMap(),
        configuration: Configuration = Configuration()
    ) {
        CaptureJniLibrary.load()

        Capture.Logger.start(
            apiKey = FAKE_API_KEY,
            apiUrl = FAKE_API_URL,
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            sessionStrategy = SessionStrategy.Fixed(),
            initialFields = initialFields,
            configuration = configuration,
        )
    }

    @After
    fun tearDown() {
        destroyCurrentLogger()
        Capture.Logger.resetShared()
    }

    @Test
    fun logNotMatchedNoFields() {
        startLogger()

        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo { LOG_MESSAGE }
        }
    }

    @Test
    fun logNotMatched5000Fields() {
        logNotMatchedFields(5000)
    }

    @Test
    fun logNotMatched1Field() {
        logNotMatchedFields(1)
    }

    @Test
    fun logNotMatched10Fields() {
        logNotMatchedFields(10)
    }

    @Test
    fun logNotMatched50Fields() {
        logNotMatchedFields(50)
    }

    @Test
    fun logNotMatched100Fields() {
        logNotMatchedFields(100)
    }

    @Test
    fun logNotMatched1000Fields() {
        logNotMatchedFields(1000)
    }

    private fun logNotMatchedFields(fieldCount: Int) {
        startLogger()
        val fields = buildFieldsMap(fieldCount)

        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo(fields) { LOG_MESSAGE }
        }
    }

    @Test
    fun trackSpansWithoutFields() {
        startLogger(createInitialFields())

        benchmarkRule.measureRepeated {
            val span = Capture.Logger.startSpan("Span without metadata", LogLevel.INFO)
            span?.end(SpanResult.SUCCESS)
        }
    }

    @Test
    fun trackSpansWithFields() {
        startLogger(createInitialFields())
        val metadata = buildFieldsMap(500)

        benchmarkRule.measureRepeated {
            val span = Capture.Logger.startSpan("Span with metadata", LogLevel.INFO, metadata)
            span?.end(SpanResult.SUCCESS)
        }
    }

    @Test
    fun logHttpNetworkLog50FieldsAndHeadersAndInitialFields() {
        startLogger(createInitialFields())

        val extraFields = buildFieldsMap(50)
        val headers = buildFieldsMap(50, keyIdentifier = "header_")

        benchmarkRule.measureRepeated {
            val request = HttpRequestInfo(
                method = "GET",
                host = "www.google.com",
                path = HttpUrlPath(
                    value = "/search",
                    template = "/search"
                ),
                query = "q=Bitdrift",
                headers = headers,
                extraFields = extraFields
            )
            Capture.Logger.log(request)

            val response = HttpResponse(
                host = request.host,
                path = HttpUrlPath(request.path?.value ?: "/"),
                query = request.query,
                statusCode = 200,
                result = HttpResult.SUCCESS,
                headers = headers
            )
            val responseInfo = HttpResponseInfo(
                request = request,
                response = response,
                durationMs = 100,
                extraFields = extraFields
            )
            Capture.Logger.log(responseInfo)
        }
    }

    @Test
    fun webViewBridgeInvalidJson() {
        val handler = WebViewBridgeMessageHandler(getInternalLogger(), "automatic_full")
        val invalidJson = "invalid json {"

        benchmarkRule.measureRepeated {
            handler.log(invalidJson)
        }
    }

    @Test
    fun webViewBridgeBridgeReady() {
        val handler = WebViewBridgeMessageHandler(getInternalLogger(), "automatic_full")
        val message =
            """{"v":1,"type":"bridgeReady","url":"https://example.com","instrumentationConfig":{"capturePageViews":true,"captureErrors":false}}"""

        benchmarkRule.measureRepeated {
            handler.log(message)
        }
    }

    @Test
    fun startNewSession() = benchmarkCaptureOperation {
        Capture.Logger.startNewSession()
    }

    @Test
    fun getSdkStatus() = benchmarkCaptureOperation {
        Capture.Logger.getSdkStatus()
    }

    @Test
    fun logAppLaunchTTI() = benchmarkCaptureOperation {
        Capture.Logger.logAppLaunchTTI(1.toDuration(DurationUnit.SECONDS))
    }

    @Test
    fun setEntityId() = benchmarkCaptureOperation {
        Capture.Logger.setEntityId("fake_id")
    }

    @Test
    fun clearEntityId() = benchmarkCaptureOperation {
        Capture.Logger.clearEntityId()
    }

    @Test
    fun loggerImplCreationWithRealNativeLayer() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                CaptureJniLibrary.load()
            }
            val logger = LoggerImpl(
                apiKey = FAKE_API_KEY,
                apiUrl = FAKE_API_URL,
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                customFieldGetters = emptyList(),
                dateProvider = null,
                configuration = Configuration(),
                sessionStrategy = SessionStrategy.Fixed(),
            )
            runWithMeasurementDisabled {
                CaptureJniLibrary.shutdown(logger.loggerId)
                CaptureJniLibrary.destroyLogger(logger.loggerId)
            }
        }
    }

    @Test
    fun loggerImplCreationWithFakeNativeLayer() {
        benchmarkRule.measureRepeated {
            LoggerImpl(
                apiKey = FAKE_API_KEY,
                apiUrl = FAKE_API_URL,
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                customFieldGetters = emptyList(),
                dateProvider = null,
                bridge = FakeBridge,
                configuration = Configuration(),
                sessionStrategy = SessionStrategy.Fixed(),
                runtimeFactory = { FakeRuntime() },
                ootbFieldProviders = emptyList(),
            )
        }
    }

    private fun benchmarkCaptureOperation(
        configuration: Configuration = Configuration(),
        captureSdkOperation: () -> Unit
    ) {
        startLogger(initialFields = createInitialFields(), configuration = configuration)

        benchmarkRule.measureRepeated { captureSdkOperation() }
    }

    private fun getInternalLogger(): IInternalLogger {
        startLogger()
        return Capture.logger() as IInternalLogger
    }

    private fun destroyCurrentLogger() {
        (Capture.logger() as? IInternalLogger)?.let {
            CaptureJniLibrary.shutdown((it as io.bitdrift.capture.LoggerImpl).loggerId)
            CaptureJniLibrary.destroyLogger(it.loggerId)
        }
    }
    private fun createInitialFields(providers: Int = 5, fields: Int = 10): Map<String, String> =
        (1..providers)
            .flatMap { providerIndex ->
                (1..fields).map { fieldIndex ->
                    "provider${providerIndex}_key$fieldIndex" to "provider${providerIndex}_val$fieldIndex"
                }
            }.toMap()

    private fun buildFieldsMap(size: Int, keyIdentifier: String = "key_"): Map<String, String> =
        (1..size).associate { "$keyIdentifier$it" to "value_$it" }

    private companion object {
        const val FAKE_API_KEY = "[test_api_key]"
        val FAKE_API_URL = "https://api-test.bitdrift.dev".toHttpUrl()
    }
}
