// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.bitdrift.capture.error.ErrorReportRequest
import io.bitdrift.capture.error.ErrorReporterService
import io.bitdrift.capture.fakes.FakeDateProvider
import io.bitdrift.capture.network.okhttp.OkHttpApiClient
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.IssueReporter
import io.bitdrift.capture.reports.IIssueReporter
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ErrorReporterTest {
    private lateinit var server: MockWebServer
    private lateinit var reporter: ErrorReporterService
    private val issueReporter: IIssueReporter =
        IssueReporter(
            dateProvider = FakeDateProvider,
        )

    init {
        CaptureJniLibrary.load()
    }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val apiClient = OkHttpApiClient(server.url(""), "api-key", client = OkHttpClient())

        reporter =
            ErrorReporterService(
                listOf(FieldProvider { mapOf("foo" to "bar") }),
                apiClient,
            )

        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun verifyErrorRequestSent() {
        CaptureTestJniLibrary.sendErrorMessage("test_error", reporter)

        val request = server.takeRequest(1, TimeUnit.SECONDS)

        assertThat(request?.path).isEqualTo("/v1/sdk-errors")
        assertThat(request?.headers?.get("content-type")).isEqualTo("application/json; charset=utf-8")
        assertThat(request?.method).isEqualTo("POST")
        assertThat(request?.headers).contains(Pair("x-foo", "bar"))
        assertThat(request?.headers).contains(Pair("x-bitdrift-api-key", "api-key"))

        val jsonPayload = request?.body?.readString(Charset.defaultCharset())!!
        assertThat(jsonPayload).isEqualTo("{\"message\":\"test_error\",\"details\":\"\"}")
    }

    @Test
    fun verifyErrorReporterLimit() {
        var count = 0
        (0..4).forEach { _ ->
            count++
            CaptureTestJniLibrary.sendErrorMessage("blah", reporter)
            val r = server.takeRequest(1, TimeUnit.SECONDS)
            assertThat(r).isNotNull
        }

        assertThat(count).isEqualTo(5)

        CaptureTestJniLibrary.sendErrorMessage("blah2", reporter)

        val r = server.takeRequest(1, TimeUnit.SECONDS)
        assertThat(r).isNull()
    }

    @Test
    fun verifyStacktraceSent() {
        @Suppress("UNUSED_VARIABLE")
        val logger =
            LoggerImpl(
                apiKey = "test",
                apiUrl = testServerUrl(),
                fieldProviders = listOf(),
                dateProvider = SystemDateProvider(),
                context = ContextHolder.APP_CONTEXT,
                sessionStrategy = SessionStrategy.Fixed { "SESSION_ID" },
                configuration = Configuration(),
                errorReporter = reporter,
                issueReporter = issueReporter,
            )

        val errorHandler = ErrorHandler()

        errorHandler.handleError("something", Throwable("some exception"))

        var r = server.takeRequest(1, TimeUnit.SECONDS)
        assertThat(r).isNotNull

        val jsonPayload = r?.body?.readString(Charset.defaultCharset())!!
        val typedRequest = Gson().fromJson<ErrorReportRequest>(jsonPayload, object : TypeToken<ErrorReportRequest>() {}.type)

        // potentially more than one request in queue
        if (!typedRequest.message.contains("'something'")) {
            r = server.takeRequest()
            assertThat(r).isNotNull
        }

        assertThat(typedRequest.message).isEqualTo(
            "jni reported: 'something' failed: java.lang.Throwable: some exception",
        )
        // This should be the stack trace, just verify that the test class appears in it.
        assertThat(typedRequest.details).contains("ErrorReporterTest")
    }
}

private fun testServerUrl(): HttpUrl =
    HttpUrl
        .Builder()
        .scheme("http")
        .host("test.bitdrift.com")
        .build()
