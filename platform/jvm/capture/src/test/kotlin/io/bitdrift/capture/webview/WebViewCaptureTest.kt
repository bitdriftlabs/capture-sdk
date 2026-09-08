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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.Capture
import io.bitdrift.capture.CaptureRuntimeProvider
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.IRuntimeProvider
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.SystemDateProvider
import io.bitdrift.capture.providers.session.SessionConfiguration
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
@OptIn(ExperimentalBitdriftApi::class)
class WebViewCaptureTest {
    private lateinit var webView: WebView
    private lateinit var appContext: Context
    private val fieldsCaptor = argumentCaptor<ArrayFields>()
    private val messageCaptor = argumentCaptor<() -> String>()
    private val runtimeProvider: IRuntimeProvider = mock()

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
    fun instrument_withSdkStartedButNoWebViewConfiguration_shouldLogNotInitialized() {
        startSdk(webViewConfiguration = null)
        val spyLogger = spyLogger()

        WebViewCaptureInternals.instrumentInternally(
            webView,
            spyLogger,
            CaptureRuntimeProvider,
            WebViewInstrumentationMode.AUTOMATIC_FULL,
        )

        assertThat(webView.settings.javaScriptEnabled).isFalse()
        verify(spyLogger).log(
            eq(LogLevel.WARNING),
            fieldsCaptor.capture(),
            eq(null),
            messageCaptor.capture(),
        )
        val fields = fieldsCaptor.firstValue.toStringMap()
        assertThat(fields["reason"]).isEqualTo("WebViewConfiguration not provided")
        assertThat(fields["_source"]).isEqualTo("webview")
        assertThat(messageCaptor.firstValue()).isEqualTo("webview.notInitialized")
    }

    @Test
    fun instrument_withValidWebViewConfiguration_shouldEnableJavascriptAndLogSuccess() {
        startSdk(webViewConfiguration = WebViewConfiguration())
        val spyLogger = spyLogger()

        WebViewCaptureInternals.instrumentInternally(
            webView,
            spyLogger,
            CaptureRuntimeProvider,
            WebViewInstrumentationMode.AUTOMATIC_FULL,
        )

        assertThat(webView.settings.javaScriptEnabled).isTrue()
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
    fun instrument_whenJavascriptEnabledOnlyAndJavascriptDisabled_shouldLogAutomaticSkipWarning() {
        startSdk(webViewConfiguration = WebViewConfiguration())
        val spyLogger = spyLogger()

        WebViewCaptureInternals.instrumentInternally(
            webView,
            spyLogger,
            CaptureRuntimeProvider,
            WebViewInstrumentationMode.AUTOMATIC_JAVASCRIPT_ENABLED_ONLY,
        )

        assertThat(webView.settings.javaScriptEnabled).isFalse()
        verify(spyLogger).log(
            eq(LogLevel.WARNING),
            fieldsCaptor.capture(),
            eq(null),
            messageCaptor.capture(),
        )
        assertThat(fieldsCaptor.firstValue.toStringMap())
            .containsEntry("_instrumentation_mode", "AUTOMATIC_JAVASCRIPT_ENABLED_ONLY")
            .containsEntry("reason", "JavaScript is not already enabled")
        assertThat(messageCaptor.firstValue()).isEqualTo("webview.automaticInstrumentationSkipped")
    }

    @Test
    fun instrument_whenJavascriptEnabledOnlyAndJavascriptEnabled_shouldInstrumentWithoutChangingJavascript() {
        startSdk(webViewConfiguration = WebViewConfiguration())
        val spyLogger = spyLogger()
        webView.settings.javaScriptEnabled = true

        WebViewCaptureInternals.instrumentInternally(
            webView,
            spyLogger,
            CaptureRuntimeProvider,
            WebViewInstrumentationMode.AUTOMATIC_JAVASCRIPT_ENABLED_ONLY,
        )

        assertThat(webView.settings.javaScriptEnabled).isTrue()
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
    fun publicInstrument_shouldEnableJavascriptForExplicitlySelectedWebView() {
        startSdk(webViewConfiguration = WebViewConfiguration())

        WebViewCapture.instrument(webView)

        assertThat(webView.settings.javaScriptEnabled).isTrue()
    }

    @Test
    fun instrument_withRuntimeFeatureDisabled_shouldSkipInstrumentation() {
        startSdk(webViewConfiguration = WebViewConfiguration())
        whenever(runtimeProvider.isRuntimeFeatureEnabled(any())).thenReturn(false)

        WebViewCaptureInternals.instrumentInternally(
            webView,
            Capture.logger(),
            runtimeProvider,
            WebViewInstrumentationMode.AUTOMATIC_FULL,
        )

        assertThat(webView.settings.javaScriptEnabled).isFalse()
    }

    @Test
    fun instrument_withRuntimeFeatureEnabled_shouldProceedWithInstrumentation() {
        startSdk(webViewConfiguration = WebViewConfiguration())
        whenever(runtimeProvider.isRuntimeFeatureEnabled(any())).thenReturn(true)

        WebViewCaptureInternals.instrumentInternally(
            webView,
            Capture.logger(),
            runtimeProvider,
            WebViewInstrumentationMode.AUTOMATIC_FULL,
        )

        assertThat(webView.settings.javaScriptEnabled).isTrue()
    }

    @Suppress("DEPRECATION")
    private fun startSdk(webViewConfiguration: WebViewConfiguration?) {
        Capture.Logger.start(
            apiKey = "test",
            initialFields = emptyMap(),
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            configuration = Configuration(webViewConfiguration = webViewConfiguration),
            dateProvider = SystemDateProvider(),
            context = appContext,
        )
    }

    private fun spyLogger(): LoggerImpl {
        val logger = Capture.logger()
        return spy(logger as LoggerImpl)
    }
}
