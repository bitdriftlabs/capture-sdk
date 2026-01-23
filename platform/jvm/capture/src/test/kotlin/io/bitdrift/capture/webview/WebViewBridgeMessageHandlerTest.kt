// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.utils.toStringMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class WebViewBridgeMessageHandlerTest {
    private lateinit var logger: IInternalLogger
    private lateinit var handler: WebViewBridgeMessageHandler
    private val arrayFieldsCaptor = argumentCaptor<ArrayFields>()
    private val logMessageCaptor = argumentCaptor<() -> String>()

    @Before
    fun setUp() {
        logger = mock()
        handler = WebViewBridgeMessageHandler(logger)
    }

    @Test
    fun log_whenInvalidJson_shouldLogWarning() {
        val throwableCaptor = argumentCaptor<Throwable>()
        handler.log("invalid json {")

        verify(logger).log(
            eq(LogLevel.WARNING),
            arrayFieldsCaptor.capture(),
            throwableCaptor.capture(),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_raw"]).isEqualTo("invalid json {")
        assertThat(
            throwableCaptor.firstValue.message,
        ).contains("Expected BEGIN_OBJECT")
        assertThat(logMessageCaptor.firstValue())
            .isEqualTo("Critical error while extracting WebViewBridgeMessage")
    }

    @Test
    fun log_whenUnsupportedVersion_shouldLogWarning() {
        val message = """{"v":99,"type":"bridgeReady","timestamp":1234567890}"""

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.WARNING),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_version"]).isEqualTo("99")
        assertThat(logMessageCaptor.firstValue())
            .isEqualTo("Unsupported WebView bridge protocol version")
    }

    @Test
    fun log_whenBridgeReady_shouldLogDebug() {
        val message =
            """
            {
                "v":1,
                "type":"bridgeReady",
                "url":"https://example.com",
                "instrumentationConfig":{"capturePageViews":true,"captureErrors":false}
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        val logMessage = logMessageCaptor.firstValue()
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_config"]).isEqualTo("{\"capturePageViews\":true,\"captureErrors\":false}")
        assertThat(fields["_url"]).isEqualTo("https://example.com")
        assertThat(logMessage).isEqualTo("webview.initialized")
    }

    @Test
    fun log_whenLifecycle_shouldLogUxDebugWithAllFields() {
        val message =
            """
            {
                "v":1,
                "type":"lifecycle",
                "timestamp":1234567890,
                "event":"load",
                "performanceTime":123.45,
                "visibilityState":"visible"
            }
            """.trimIndent()

        val logTypeCaptor = argumentCaptor<LogType>()
        handler.log(message)

        verify(logger).log(
            logTypeCaptor.capture(),
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            logMessageCaptor.capture(),
        )
        assertThat(logTypeCaptor.firstValue).isEqualTo(LogType.UX)
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_event"]).isEqualTo("load")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(fields["_performance_time"]).isEqualTo("123.45")
        assertThat(fields["_visibility_state"]).isEqualTo("visible")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.lifecycle")
    }

    @Test
    fun log_whenLifecycleWithoutOptionalFields_shouldLogUxDebug() {
        val message =
            """
            {
                "v":1,
                "type":"lifecycle",
                "timestamp":1234567890,
                "event":"unload"
            }
            """.trimIndent()

        val logTypeCaptor = argumentCaptor<LogType>()
        handler.log(message)

        verify(logger).log(
            logTypeCaptor.capture(),
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            logMessageCaptor.capture(),
        )
        assertThat(logTypeCaptor.firstValue).isEqualTo(LogType.UX)
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_event"]).isEqualTo("unload")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(fields).doesNotContainKey("_performance_time")
        assertThat(fields).doesNotContainKey("_visibility_state")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.lifecycle")
    }

    @Test
    fun log_whenNavigation_shouldLogDebugWithAllFields() {
        val message =
            """
            {
                "v":1,
                "type":"navigation",
                "timestamp":1234567890,
                "fromUrl":"https://example.com/page1",
                "toUrl":"https://example.com/page2",
                "method":"pushState"
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_fromUrl"]).isEqualTo("https://example.com/page1")
        assertThat(fields["_toUrl"]).isEqualTo("https://example.com/page2")
        assertThat(fields["_method"]).isEqualTo("pushState")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.navigation")
    }

    @Test
    fun log_whenNavigationWithoutOptionalFields_shouldLogDebugWithEmptyStrings() {
        val message =
            """
            {
                "v":1,
                "type":"navigation",
                "timestamp":1234567890
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_fromUrl"]).isEqualTo("")
        assertThat(fields["_toUrl"]).isEqualTo("")
        assertThat(fields["_method"]).isEqualTo("")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.navigation")
    }

    @Test
    fun log_whenError_shouldLogErrorWithAllFields() {
        val message =
            """
            {
                "v":1,
                "type":"error",
                "timestamp":1234567890,
                "name":"TypeError",
                "message":"Cannot read property 'foo' of undefined",
                "stack":"TypeError: Cannot read property 'foo' of undefined\n    at main.js:10:5",
                "filename":"https://example.com/main.js",
                "lineno":10,
                "colno":5
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.ERROR),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_name"]).isEqualTo("TypeError")
        assertThat(fields["_message"]).isEqualTo("Cannot read property 'foo' of undefined")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(fields["_stack"]).isEqualTo("TypeError: Cannot read property 'foo' of undefined\n    at main.js:10:5")
        assertThat(fields["_filename"]).isEqualTo("https://example.com/main.js")
        assertThat(fields["_lineno"]).isEqualTo("10")
        assertThat(fields["_colno"]).isEqualTo("5")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.error")
    }

    @Test
    fun log_whenErrorWithoutOptionalFields_shouldLogErrorWithDefaults() {
        val message =
            """
            {
                "v":1,
                "type":"error",
                "timestamp":1234567890
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.ERROR),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_name"]).isEqualTo("Error")
        assertThat(fields["_message"]).isEqualTo("Unknown error")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(fields).doesNotContainKey("_stack")
        assertThat(fields).doesNotContainKey("_filename")
        assertThat(fields).doesNotContainKey("_lineno")
        assertThat(fields).doesNotContainKey("_colno")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.error")
    }

    @Test
    fun log_whenLongTask200Ms_shouldLogUxWarning() {
        val message =
            """
            {
                "v":1,
                "type":"longTask",
                "timestamp":1234567890,
                "durationMs":250,
                "startTime":100.5,
                "attribution":{
                    "name":"self",
                    "containerType":"iframe",
                    "containerSrc":"https://example.com",
                    "containerId":"myFrame",
                    "containerName":"frameOne"
                }
            }
            """.trimIndent()

        val logTypeCaptor = argumentCaptor<LogType>()
        handler.log(message)

        verify(logger).log(
            logTypeCaptor.capture(),
            eq(LogLevel.WARNING),
            arrayFieldsCaptor.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            logMessageCaptor.capture(),
        )
        assertThat(logTypeCaptor.firstValue).isEqualTo(LogType.UX)
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_duration_ms"]).isEqualTo("250.0")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(fields["_start_time"]).isEqualTo("100.5")
        assertThat(fields["_attribution_name"]).isEqualTo("self")
        assertThat(fields["_container_type"]).isEqualTo("iframe")
        assertThat(fields["_container_src"]).isEqualTo("https://example.com")
        assertThat(fields["_container_id"]).isEqualTo("myFrame")
        assertThat(fields["_container_name"]).isEqualTo("frameOne")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.longTask")
    }

    @Test
    fun log_whenLongTask100To199Ms_shouldLogUxInfo() {
        val message =
            """
            {
                "v":1,
                "type":"longTask",
                "timestamp":1234567890,
                "durationMs":150
            }
            """.trimIndent()

        val logTypeCaptor = argumentCaptor<LogType>()
        handler.log(message)

        verify(logger).log(
            logTypeCaptor.capture(),
            eq(LogLevel.INFO),
            arrayFieldsCaptor.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            logMessageCaptor.capture(),
        )
        assertThat(logTypeCaptor.firstValue).isEqualTo(LogType.UX)
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_duration_ms"]).isEqualTo("150.0")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.longTask")
    }

    @Test
    fun log_whenLongTaskUnder100Ms_shouldLogUxDebug() {
        val message =
            """
            {
                "v":1,
                "type":"longTask",
                "timestamp":1234567890,
                "durationMs":75
            }
            """.trimIndent()

        val logTypeCaptor = argumentCaptor<LogType>()
        handler.log(message)

        verify(logger).log(
            logTypeCaptor.capture(),
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            logMessageCaptor.capture(),
        )
        assertThat(logTypeCaptor.firstValue).isEqualTo(LogType.UX)
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_duration_ms"]).isEqualTo("75.0")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.longTask")
    }

    @Test
    fun log_whenResourceError_shouldLogWarningWithAllFields() {
        val message =
            """
            {
                "v":1,
                "type":"resourceError",
                "timestamp":1234567890,
                "resourceType":"script",
                "url":"https://example.com/script.js",
                "tagName":"SCRIPT"
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.WARNING),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_resource_type"]).isEqualTo("script")
        assertThat(fields["_url"]).isEqualTo("https://example.com/script.js")
        assertThat(fields["_tag_name"]).isEqualTo("SCRIPT")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.resourceError")
    }

    @Test
    fun log_whenResourceErrorWithoutOptionalFields_shouldLogWarningWithDefaults() {
        val message =
            """
            {
                "v":1,
                "type":"resourceError",
                "timestamp":1234567890
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.WARNING),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_resource_type"]).isEqualTo("unknown")
        assertThat(fields["_url"]).isEqualTo("")
        assertThat(fields["_tag_name"]).isEqualTo("")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.resourceError")
    }

    @Test
    fun log_whenConsoleError_shouldLogError() {
        val message =
            """
            {
                "v":1,
                "type":"console",
                "timestamp":1234567890,
                "level":"error",
                "message":"Something went wrong",
                "args":["arg1", "arg2", "arg3"]
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.ERROR),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_level"]).isEqualTo("error")
        assertThat(fields["_message"]).isEqualTo("Something went wrong")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(fields["_args"]).isEqualTo("arg1, arg2, arg3")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.console")
    }

    @Test
    fun log_whenConsoleWarn_shouldLogWarning() {
        val message =
            """
            {
                "v":1,
                "type":"console",
                "timestamp":1234567890,
                "level":"warn",
                "message":"Warning message"
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.WARNING),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_level"]).isEqualTo("warn")
        assertThat(fields["_message"]).isEqualTo("Warning message")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.console")
    }

    @Test
    fun log_whenConsoleInfo_shouldLogInfo() {
        val message =
            """
            {
                "v":1,
                "type":"console",
                "timestamp":1234567890,
                "level":"info",
                "message":"Info message"
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.INFO),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_level"]).isEqualTo("info")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.console")
    }

    @Test
    fun log_whenConsoleLogOrDebug_shouldLogDebug() {
        val message =
            """
            {
                "v":1,
                "type":"console",
                "timestamp":1234567890,
                "level":"log",
                "message":"Debug message"
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_level"]).isEqualTo("log")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.console")
    }

    @Test
    fun log_whenConsoleWithManyArgs_shouldTruncateToFirst5() {
        val message =
            """
            {
                "v":1,
                "type":"console",
                "timestamp":1234567890,
                "level":"log",
                "message":"Many args",
                "args":["arg1", "arg2", "arg3", "arg4", "arg5", "arg6", "arg7"]
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_args"]).isEqualTo("arg1, arg2, arg3, arg4, arg5")
    }

    @Test
    fun log_whenConsoleWithoutOptionalFields_shouldLogWithDefaults() {
        val message =
            """
            {
                "v":1,
                "type":"console",
                "timestamp":1234567890
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_level"]).isEqualTo("log")
        assertThat(fields["_message"]).isEqualTo("")
        assertThat(fields).doesNotContainKey("_args")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.console")
    }

    @Test
    fun log_whenPromiseRejection_shouldLogErrorWithAllFields() {
        val message =
            """
            {
                "v":1,
                "type":"promiseRejection",
                "timestamp":1234567890,
                "reason":"Network request failed",
                "stack":"Error: Network request failed\n    at fetch.js:20:10"
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.ERROR),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_reason"]).isEqualTo("Network request failed")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_stack"]).isEqualTo("Error: Network request failed\n    at fetch.js:20:10")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.promiseRejection")
    }

    @Test
    fun log_whenPromiseRejectionWithoutOptionalFields_shouldLogErrorWithDefaults() {
        val message =
            """
            {
                "v":1,
                "type":"promiseRejection",
                "timestamp":1234567890
            }
            """.trimIndent()

        handler.log(message)

        verify(logger).log(
            eq(LogLevel.ERROR),
            arrayFieldsCaptor.capture(),
            eq(null),
            logMessageCaptor.capture(),
        )
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_reason"]).isEqualTo("Unknown rejection")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields).doesNotContainKey("_stack")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.promiseRejection")
    }

    @Test
    fun log_whenUserInteractionClick_shouldLogUxDebug() {
        val message =
            """
            {
                "v":1,
                "type":"userInteraction",
                "timestamp":1234567890,
                "interactionType":"click",
                "tagName":"BUTTON",
                "isClickable":true,
                "elementId":"submit-btn",
                "className":"btn btn-primary",
                "textContent":"Submit"
            }
            """.trimIndent()

        val logTypeCaptor = argumentCaptor<LogType>()
        handler.log(message)

        verify(logger).log(
            logTypeCaptor.capture(),
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            logMessageCaptor.capture(),
        )
        assertThat(logTypeCaptor.firstValue).isEqualTo(LogType.UX)
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_interaction_type"]).isEqualTo("click")
        assertThat(fields["_tag_name"]).isEqualTo("BUTTON")
        assertThat(fields["_is_clickable"]).isEqualTo("true")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(fields["_timestamp"]).isEqualTo("1234567890")
        assertThat(fields["_element_id"]).isEqualTo("submit-btn")
        assertThat(fields["_class_name"]).isEqualTo("btn btn-primary")
        assertThat(fields["_text_content"]).isEqualTo("Submit")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.userInteraction")
    }

    @Test
    fun log_whenUserInteractionRageClick_shouldLogUxWarning() {
        val message =
            """
            {
                "v":1,
                "type":"userInteraction",
                "timestamp":1234567890,
                "interactionType":"rageClick",
                "tagName":"DIV",
                "isClickable":false,
                "clickCount":5,
                "timeWindowMs":1000
            }
            """.trimIndent()

        val logTypeCaptor = argumentCaptor<LogType>()
        handler.log(message)

        verify(logger).log(
            logTypeCaptor.capture(),
            eq(LogLevel.WARNING),
            arrayFieldsCaptor.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            logMessageCaptor.capture(),
        )
        assertThat(logTypeCaptor.firstValue).isEqualTo(LogType.UX)
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_interaction_type"]).isEqualTo("rageClick")
        assertThat(fields["_tag_name"]).isEqualTo("DIV")
        assertThat(fields["_is_clickable"]).isEqualTo("false")
        assertThat(fields["_click_count"]).isEqualTo("5")
        assertThat(fields["_time_window_ms"]).isEqualTo("1000")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.userInteraction")
    }

    @Test
    fun log_whenUserInteractionWithMinimalFields_shouldLogUxDebug() {
        val message =
            """
            {
                "v":1,
                "type":"userInteraction",
                "timestamp":1234567890,
                "interactionType":"hover"
            }
            """.trimIndent()

        val logTypeCaptor = argumentCaptor<LogType>()
        handler.log(message)

        verify(logger).log(
            logTypeCaptor.capture(),
            eq(LogLevel.DEBUG),
            arrayFieldsCaptor.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            logMessageCaptor.capture(),
        )
        assertThat(logTypeCaptor.firstValue).isEqualTo(LogType.UX)
        val fields = arrayFieldsCaptor.firstValue.toStringMap()
        assertThat(fields["_interaction_type"]).isEqualTo("hover")
        assertThat(fields["_tag_name"]).isEqualTo("")
        assertThat(fields["_is_clickable"]).isEqualTo("false")
        assertThat(fields).doesNotContainKey("_element_id")
        assertThat(fields).doesNotContainKey("_class_name")
        assertThat(fields).doesNotContainKey("_text_content")
        assertThat(fields).doesNotContainKey("_click_count")
        assertThat(fields).doesNotContainKey("_time_window_ms")
        assertThat(logMessageCaptor.firstValue()).isEqualTo("webview.userInteraction")
    }
}
