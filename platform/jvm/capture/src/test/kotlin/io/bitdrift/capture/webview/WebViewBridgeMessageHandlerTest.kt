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
}
