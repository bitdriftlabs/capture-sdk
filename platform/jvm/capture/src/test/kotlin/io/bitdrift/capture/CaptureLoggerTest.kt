// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.attributes.DeviceAttributes
import io.bitdrift.capture.attributes.NetworkAttributes
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.reports.CrashReporterState
import io.bitdrift.capture.reports.CrashReporterStatus
import io.bitdrift.capture.threading.CaptureDispatchers
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Date
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// This should return "2022-07-05T18:55:58.123Z" when formatted.
private const val TEST_DATE_TIMESTAMP: Long = 1657047358123

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class CaptureLoggerTest {
    private val systemDateProvider =
        DateProvider {
            Date(TEST_DATE_TIMESTAMP)
        }

    private lateinit var logger: LoggerImpl
    private var testServerPort: Int? = null

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        CaptureDispatchers.setTestExecutorService(MoreExecutors.newDirectExecutorService())
        CaptureJniLibrary.load()

        testServerPort = CaptureTestJniLibrary.startTestApiServer(-1)

        logger = buildLogger(dateProvider = systemDateProvider)
    }

    @After
    fun teardown() {
        if (testServerPort != null) {
            CaptureTestJniLibrary.stopTestApiServer()
            testServerPort = null
        }
    }

    @Test
    fun propertiesReturnsCorrectValues() {
        assertThat(logger.deviceId.length).isEqualTo(36)
        assertThat(logger.sessionId.length).isEqualTo(10)
    }

    // Verifies how top level typed helpers map to a typed logging call.
    @Test
    fun typedLogging() {
        val logger =
            spy(buildLogger(dateProvider = SystemDateProvider()))

        val spanId = UUID.randomUUID()

        val requestInfo =
            HttpRequestInfo(
                host = "api.bitdrift.io",
                method = "GET",
                path = HttpUrlPath("/my_path/12345"),
                query = "my=query",
                headers = mapOf("request_header" to "request_value"),
                spanId = spanId,
                extraFields = mapOf("my_extra_key_1" to "my_extra_value_1"),
            )

        logger.log(requestInfo)

        val expectedRequestFields =
            mapOf(
                "_host" to "api.bitdrift.io",
                "_method" to "GET",
                "_path" to "/my_path/12345",
                "_query" to "my=query",
                "_span_id" to spanId.toString(),
                "_span_name" to "_http",
                "_span_type" to "start",
                "my_extra_key_1" to "my_extra_value_1",
            ).toFields()

        val expectedRequestMatchingFields =
            mapOf(
                "_headers.request_header" to "request_value",
            ).toFields()

        Mockito.verify(logger).log(
            eq(LogType.SPAN),
            eq(LogLevel.DEBUG),
            eq(expectedRequestFields),
            eq(expectedRequestMatchingFields),
            eq(null),
            eq(false),
            argThat { i -> i.invoke() == requestInfo.name },
        )

        val responseInfo =
            HttpResponseInfo(
                request = requestInfo,
                response =
                    HttpResponse(
                        result = HttpResponse.HttpResult.SUCCESS,
                        error = RuntimeException("my_error"),
                        headers = mapOf("response_header" to "response_value"),
                    ),
                durationMs = 60L,
                extraFields = mapOf("my_extra_key_2" to "my_extra_value_2"),
            )

        logger.log(responseInfo)

        val expectedResponseFields =
            mapOf(
                "_host" to "api.bitdrift.io",
                "_method" to "GET",
                "_path" to "/my_path/12345",
                "_query" to "my=query",
                "_span_id" to spanId.toString(),
                "_span_name" to "_http",
                "_span_type" to "end",
                "_duration_ms" to "60",
                "_result" to "success",
                "_error_type" to "RuntimeException",
                "_error_message" to "my_error",
                "my_extra_key_1" to "my_extra_value_1",
                "my_extra_key_2" to "my_extra_value_2",
            ).toFields()

        val expectedResponseMatchingFields =
            mapOf(
                "_request._host" to "api.bitdrift.io",
                "_request._method" to "GET",
                "_request._path" to "/my_path/12345",
                "_request._span_id" to spanId.toString(),
                "_request._span_name" to "_http",
                "_request._span_type" to "start",
                "_request._query" to "my=query",
                "_request.my_extra_key_1" to "my_extra_value_1",
                "_request._headers.request_header" to "request_value",
                "_headers.response_header" to "response_value",
            ).toFields()

        Mockito.verify(logger).log(
            eq(LogType.SPAN),
            eq(LogLevel.DEBUG),
            eq(expectedResponseFields),
            eq(expectedResponseMatchingFields),
            eq(null),
            eq(false),
            argThat { i -> i.invoke() == responseInfo.name },
        )
    }

    @Test
    fun normalLogExtractsThrowableInfo() {
        val logger =
            spy(buildLogger(dateProvider = SystemDateProvider()))

        val msg = "my_message"
        logger.log(LogLevel.ERROR, throwable = IOException("my_error")) { msg }

        val expectedFields =
            mapOf(
                "_error" to "java.io.IOException",
                "_error_details" to "my_error",
            ).toFields()

        verify(logger).log(
            eq(LogType.NORMAL),
            eq(LogLevel.ERROR),
            eq(expectedFields),
            eq(null),
            eq(null),
            eq(false),
            argThat { i -> i.invoke() == msg },
        )
    }

    @Test
    fun logger_client_metadata() {
        logger.log(LogLevel.ERROR) { "log" }

        @Suppress("UNUSED_VARIABLE")
        val deviceId = logger.deviceId
        val apiStreamId = CaptureTestJniLibrary.awaitNextApiStream()

        assertThat(
            CaptureTestJniLibrary.awaitApiServerReceivedHandshake(
                apiStreamId,
                mapOf(
                    "app_id" to "io.bitdrift.capture",
                    "app_version" to "?.?.?",
                    "os" to "android",
                    "device_id" to deviceId,
                    "platform" to "android",
                    "model" to "robolectric",
                ),
                listOf("sdk_version", "config_version"),
            ),
        ).isTrue
    }

    @Test
    fun test_global_fields() {
        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(streamId).isNotEqualTo(-1)

        CaptureTestJniLibrary.configureAggressiveContinuousUploads(streamId)

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

        val sdkConfiguredLog = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(sdkConfiguredLog.message).isEqualTo("SDKConfigured")

        val resourceLog = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(resourceLog.message).isEqualTo("")

        val log = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(log.level).isEqualTo(LogLevel.DEBUG.value)
        assertThat(log.message).isEqualTo("test log")
        assertThat(log.fields).isEqualTo(expectedFields)
        assertThat(log.sessionId).isEqualTo("SESSION_ID")
        assertThat(log.rfc3339Timestamp).isEqualTo("2022-07-05T18:55:58.123Z")
    }

    @Test
    @Config(qualifiers = "+ar") // test arabic date parsing
    fun test_logger_end_to_end() {
        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(streamId).isNotEqualTo(-1)

        CaptureTestJniLibrary.configureAggressiveContinuousUploads(streamId)

        logger.log(LogLevel.DEBUG, fields = mapOf("fields" to "passed_in")) { "test log" }

        val expectedFields =
            mapOf(
                "fields" to "passed_in".toFieldValue(),
            ) + getDefaultFields()

        val sdkConfigured = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(sdkConfigured.message).isEqualTo("SDKConfigured")

        var log = CaptureTestJniLibrary.nextUploadedLog()

        // Sometimes a resource log is sent before the actual log, so skip it to make the tests more
        // stable.
        if (log.fields.containsKey("_battery_val")) {
            log = CaptureTestJniLibrary.nextUploadedLog()
        }
        assertThat(log.level).isEqualTo(LogLevel.DEBUG.value)
        assertThat(log.fields).isEqualTo(expectedFields)
        assertThat(log.message).isEqualTo("test log")
        assertThat(log.sessionId).isEqualTo("SESSION_ID")
        assertThat(log.rfc3339Timestamp).isEqualTo("2022-07-05T18:55:58.123Z")
    }

    @Test
    fun thread_local_recursive_logging() {
        // Set a provider which would perform recursive logging.
        val dateProvider =
            DateProvider {
                logger.log(LogLevel.DEBUG) { "should never be logged" }
                Date()
            }

        // Mock one of the providers so we can tell how many times we're logging.
        val fieldProvider = mock<FieldProvider>()
        Mockito.`when`(fieldProvider.invoke()).thenReturn(mapOf("test_key" to "test_value"))

        resetLogger(buildLogger(fieldProvider, dateProvider))

        // Log twice, then verify that the grouping provider is hit exactly twice. If we recursed,
        // we'd have hit a stack overflow or >2 calls to the grouping provider.
        logger.log(LogLevel.DEBUG) { "logging..." }
        logger.log(LogLevel.DEBUG) { "logging..." }

        Mockito.verify(fieldProvider, timeout(250).times(3)).invoke()
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun exceptions_thrown_by_session_strategy_are_ignored() {
        val providerLatch = CountDownLatch(1)

        val dateProvider = mock<DateProvider>()
        Mockito.`when`(dateProvider.invoke()).thenReturn(Date())

        val fieldProvider = mock<FieldProvider>()
        Mockito.`when`(fieldProvider.invoke()).thenReturn(emptyMap())

        resetLogger(
            buildLogger(
                fieldProvider,
                dateProvider,
                SessionStrategy.Fixed {
                    providerLatch.countDown()
                    throw RuntimeException()
                },
            ),
        )

        logger.log(LogLevel.DEBUG) { "logging..." }
        assert(providerLatch.await(1, TimeUnit.SECONDS))
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun exceptions_thrown_by_date_provider_are_ignored() {
        val providerLatch = CountDownLatch(1)

        val dateProvider =
            DateProvider {
                // providers are called on a background thread. `countDown` on a latch
                // to inform the test that the provider has been called into.
                providerLatch.countDown()
                throw RuntimeException("test")
            }

        val fieldProvider = mock<FieldProvider>()
        Mockito.`when`(fieldProvider.invoke()).thenReturn(emptyMap())

        resetLogger(buildLogger(fieldProvider, dateProvider))

        logger.log(LogLevel.DEBUG) { "logging..." }
        assert(providerLatch.await(1, TimeUnit.SECONDS))
    }

    @Suppress("TooGenericExceptionThrown")
    fun exceptions_thrown_by_field_provider_are_ignored() {
        val providerLatch = CountDownLatch(1)

        val dateProvider = mock<DateProvider>()
        Mockito.`when`(dateProvider.invoke()).thenReturn(Date())

        val fieldProvider =
            FieldProvider {
                // providers are called on a background thread. `countDown` on a latch
                // to inform the test that the provider has been called into.
                providerLatch.countDown()
                throw RuntimeException("test")
            }

        resetLogger(buildLogger(fieldProvider, dateProvider))

        logger.log(LogLevel.DEBUG) { "logging..." }
        assert(providerLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun log_operation_swallows_errors() {
        logger.log(LogLevel.DEBUG) { throw RuntimeException("crash") }
    }

    @Test
    fun jni_runtime() {
        assertThat(JniRuntime(logger.loggerId).isEnabled(RuntimeFeature.SESSION_REPLAY_COMPOSE)).isTrue

        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        assertThat(streamId).isNotEqualTo(-1)

        assertThat(CaptureTestJniLibrary.awaitApiServerReceivedHandshake(streamId)).isTrue

        CaptureTestJniLibrary.disableRuntimeFeature(
            streamId,
            RuntimeFeature.SESSION_REPLAY_COMPOSE.featureName,
        )

        assertThat(JniRuntime(logger.loggerId).isEnabled(RuntimeFeature.SESSION_REPLAY_COMPOSE)).isFalse
    }

    private fun testServerUrl(): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("http")
            .host("localhost")
            .port(testServerPort!!)
            .build()

    private fun resetLogger(logger: LoggerImpl) {
        this.logger = logger
    }

    private fun buildLogger(
        fieldProvider: FieldProvider? = null,
        dateProvider: DateProvider = mock<DateProvider>(),
        sessionStrategy: SessionStrategy = SessionStrategy.Fixed { "SESSION_ID" },
        crashReporterStatus: CrashReporterStatus = CrashReporterStatus(CrashReporterState.NotInitialized),
    ): LoggerImpl {
        val fieldProviders = fieldProvider?.let { listOf(it) }.orEmpty()
        return LoggerImpl(
            apiKey = "test",
            apiUrl = testServerUrl(),
            fieldProviders = fieldProviders,
            sessionStrategy = sessionStrategy,
            dateProvider = dateProvider,
            configuration = Configuration(),
            crashReporterStatus = crashReporterStatus,
        )
    }

    private fun getDefaultFields(): Map<String, FieldValue> =
        ClientAttributes(
            ContextHolder.APP_CONTEXT,
            ProcessLifecycleOwner.get(),
        ).invoke().toFields() +
            NetworkAttributes(ContextHolder.APP_CONTEXT).invoke().toFields() +
            DeviceAttributes(ContextHolder.APP_CONTEXT).invoke().toFields()
}
