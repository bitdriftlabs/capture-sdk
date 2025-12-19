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
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponse.HttpResult
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.timber.CaptureTree
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    private fun startLogger(fieldProviders: List<FieldProvider> = listOf()) {
        CaptureJniLibrary.load()
        Capture.Logger.start(
            apiKey = "[test_api_key]",
            apiUrl = "https://api-test.bitdrift.dev".toHttpUrl(),
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            sessionStrategy = SessionStrategy.Fixed(),
            fieldProviders = fieldProviders
        )
    }

    private fun createFieldProviders(providers: Int = 5, fields: Int = 10): List<FieldProvider> {
        return (1..providers).map { providerIndex ->
            val fields = (1..fields).associate { fieldIndex ->
                "provider${providerIndex}_key$fieldIndex" to "provider${providerIndex}_val$fieldIndex"
            }
            FieldProvider { fields }
        }
    }

    @Test
    fun logNotMatchedNoFields() {
        startLogger()

        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo { LOG_MESSAGE }
        }
    }

    @Test
    fun logNotMatched5Fields() {
        startLogger()
        val fields = buildFieldsMap(5)

        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo(fields) { LOG_MESSAGE }
        }
    }

    @Test
    fun logNotMatched10Fields() {
        startLogger()
        val fields = buildFieldsMap(10)

        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo(fields) { LOG_MESSAGE }
        }
    }

    @Test
    fun logNotMatched500Fields() {
        startLogger()
        val fields = buildFieldsMap(500)

        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo(fields) { LOG_MESSAGE }
        }
    }

    @Test
    fun logNotMatched5000Fields() {
        startLogger()
        val fields = buildFieldsMap(5000)

        benchmarkRule.measureRepeated {
            Capture.Logger.logInfo(fields) { LOG_MESSAGE }
        }
    }

    @Test
    fun trackSpansWithoutFields() {
        startLogger(createFieldProviders())

        benchmarkRule.measureRepeated {
            val span = Capture.Logger.startSpan("Span without metadata", LogLevel.INFO)
            span?.end(SpanResult.SUCCESS)
        }
    }

    @Test
    fun trackSpansWithFields() {
        startLogger(createFieldProviders())
        val metadata = buildFieldsMap(500)

        benchmarkRule.measureRepeated {
            val span = Capture.Logger.startSpan("Span with metadata", LogLevel.INFO, metadata)
            span?.end(SpanResult.SUCCESS)
        }
    }

    @Test
    fun logHttpNetworkLog50FieldsAndHeadersAndFieldProviders() {
        startLogger(createFieldProviders())

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
    fun timberLogWithCaptureAndNoTag() {
        startLogger()
        plantCaptureTree()

        benchmarkRule.measureRepeated {
            Timber.i(LOG_MESSAGE)
        }
    }

    @Test
    fun timberLogWithCaptureAndTag() {
        startLogger()
        plantCaptureTree()

        benchmarkRule.measureRepeated {
            Timber.tag("MyTag").i(LOG_MESSAGE)
        }
    }

    @Test
    fun timberLogWithCaptureAndError() {
        startLogger()
        plantCaptureTree()
        val exception = RuntimeException("test error")

        benchmarkRule.measureRepeated {
            Timber.e(exception, LOG_MESSAGE)
        }
    }

    @Test
    fun timberLogWithCaptureAndErrorAndTag() {
        startLogger()
        plantCaptureTree()
        val exception = RuntimeException("test error")

        benchmarkRule.measureRepeated {
            Timber.tag("MyTag").e(exception, LOG_MESSAGE)
        }
    }

    @Test
    fun timberLogWithoutCaptureAndErrorAndTag() {
        Timber.uprootAll()
        val exception = RuntimeException("test error")

        benchmarkRule.measureRepeated {
            Timber.tag("MyTag").e(exception, LOG_MESSAGE)
        }
    }

    private fun plantCaptureTree() {
        Timber.uprootAll()
        Timber.plant(CaptureTree())
    }

    private fun buildFieldsMap(size: Int, keyIdentifier: String = "key_"): Map<String, String> =
        (1..size).associate { "$keyIdentifier$it" to "value_$it" }
}
