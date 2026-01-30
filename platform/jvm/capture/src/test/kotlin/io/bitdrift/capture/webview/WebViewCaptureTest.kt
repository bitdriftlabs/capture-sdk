// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.utils.toStringMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], shadows = [ShadowWebViewFeature::class, ShadowWebViewCompat::class])
class WebViewCaptureTest {
    private lateinit var webView: WebView
    private lateinit var appContext: Context
    private val fieldsCaptor = argumentCaptor<ArrayFields>()
    private val messageCaptor = argumentCaptor<() -> String>()

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext()
        val initializer = ContextHolder()
        initializer.create(appContext)
        webView = WebView(appContext)
    }

    @After
    fun tearDown() {
        Capture.Logger.resetShared()
    }

    @Test
    fun instrument_withoutSdkStarted_shouldNotEnableJavascript() {
        WebViewCapture.instrument(webView)

        assertThat(webView.settings.javaScriptEnabled).isFalse()
    }

    @Test
    fun instrument_withValidWebViewConfigurationAndJsEnabled_shouldLogSuccess() {
        startSdk(webViewConfiguration = WebViewConfiguration())
        val spyLogger = spyLogger()
        webView.settings.javaScriptEnabled = true

        WebViewCapture.instrument(webView, spyLogger)

        verify(spyLogger).logInternal(
            eq(LogType.INTERNALSDK),
            eq(LogLevel.DEBUG),
            eq(ArrayFields.EMPTY),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            messageCaptor.capture(),
        )
        assertThat(messageCaptor.firstValue()).isEqualTo("WebView bridge script injected successfully")
    }

    @Test
    fun instrument_withSdkStartedButNoWebViewConfiguration_shouldLogNotInitialized() {
        startSdk(webViewConfiguration = null)
        val spyLogger = spyLogger()

        WebViewCapture.instrument(webView, spyLogger)

        assertNotInitializedMessage(spyLogger, "WebViewConfiguration not provided")
    }

    @Test
    fun instrument_withValidWebViewConfigurationAndJsDisabled_shouldLogNotInitMessage() {
        startSdk(webViewConfiguration = WebViewConfiguration())
        val spyLogger = spyLogger()
        webView.settings.javaScriptEnabled = false

        WebViewCapture.instrument(webView, spyLogger)

        assertThat(webView.settings.javaScriptEnabled).isFalse()
        assertNotInitializedMessage(spyLogger, "webview.settings.javaScriptEnabled is set to false")
    }

    private fun startSdk(webViewConfiguration: WebViewConfiguration?) {
        Capture.Logger.start(
            apiKey = "test",
            sessionStrategy = SessionStrategy.Fixed(),
            configuration = Configuration(webViewConfiguration = webViewConfiguration),
            dateProvider = SystemDateProvider(),
            context = appContext,
        )
    }

    private fun assertNotInitializedMessage(spyLogger: LoggerImpl, reason: String) {
        verify(spyLogger).log(
            eq(LogLevel.WARNING),
            fieldsCaptor.capture(),
            eq(null),
            messageCaptor.capture(),
        )
        val fields = fieldsCaptor.firstValue.toStringMap()
        assertThat(fields["reason"]).isEqualTo(reason)
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(messageCaptor.firstValue()).isEqualTo("webview.notInitialized")
    }

    private fun spyLogger(): LoggerImpl {
        val logger = Capture.logger()
        return spy(logger as LoggerImpl)
    }
}
