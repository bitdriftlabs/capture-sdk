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
    fun instrument_withSdkStartedButNoWebViewConfiguration_shouldLogNotInitialized() {
        startSdk(webViewConfiguration = null)
        val spyLogger = spyLogger()

        WebViewCapture.instrument(webView, spyLogger)

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
    @Suppress("DEPRECATION")
    fun instrument_withDeprecatedConstructor_shouldEnableJavascriptAndLogSuccess() {
        startSdk(webViewConfiguration = WebViewConfiguration())
        val spyLogger = spyLogger()

        WebViewCapture.instrument(webView, spyLogger)

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
    fun instrument_withFullWithJavaScriptMode_shouldEnableJavascript() {
        val config =
            WebViewConfiguration
                .builder()
                .instrumentationMode(WebViewInstrumentationMode.FULL_WITH_JAVASCRIPT)
                .capturePageViews(true)
                .build()
        startSdk(webViewConfiguration = config)

        WebViewCapture.instrument(webView)

        assertThat(webView.settings.javaScriptEnabled).isTrue()
        assertThat(webView.webViewClient).isInstanceOf(NativeWebViewClient::class.java)
    }

    @Test
    fun instrument_withNativeOnlyMode_shouldNotEnableJavascript() {
        val config = WebViewConfiguration.nativeOnly()
        startSdk(webViewConfiguration = config)

        WebViewCapture.instrument(webView)

        assertThat(webView.settings.javaScriptEnabled).isFalse()
        assertThat(webView.webViewClient).isInstanceOf(NativeWebViewClient::class.java)
    }

    @Test
    fun instrument_withNativeOnlyMode_shouldWrapExistingWebViewClient() {
        val config = WebViewConfiguration.nativeOnly()
        startSdk(webViewConfiguration = config)

        WebViewCapture.instrument(webView)

        assertThat(webView.webViewClient).isInstanceOf(NativeWebViewClient::class.java)
    }

    @Test
    fun instrument_calledTwice_shouldOnlyInstrumentOnce() {
        val config = WebViewConfiguration.fullWithJavaScript()
        startSdk(webViewConfiguration = config)

        WebViewCapture.instrument(webView)
        val firstClient = webView.webViewClient

        WebViewCapture.instrument(webView)
        val secondClient = webView.webViewClient

        assertThat(firstClient).isSameAs(secondClient)
    }

    @Test
    fun fullWithJavaScript_factoryMethod_shouldHaveCorrectDefaults() {
        val config = WebViewConfiguration.fullWithJavaScript()

        assertThat(config.instrumentationMode).isEqualTo(WebViewInstrumentationMode.FULL_WITH_JAVASCRIPT)
        assertThat(config.capturePageViews).isTrue()
        assertThat(config.captureNetworkRequests).isTrue()
        assertThat(config.captureNavigationEvents).isTrue()
        assertThat(config.captureWebVitals).isTrue()
        assertThat(config.captureLongTasks).isTrue()
        assertThat(config.captureConsoleLogs).isTrue()
        assertThat(config.captureUserInteractions).isTrue()
        assertThat(config.captureErrors).isTrue()
    }

    @Test
    fun nativeOnly_factoryMethod_shouldHaveCorrectDefaults() {
        val config = WebViewConfiguration.nativeOnly()

        assertThat(config.instrumentationMode).isEqualTo(WebViewInstrumentationMode.NATIVE_ONLY)
        assertThat(config.capturePageViews).isTrue()
        assertThat(config.captureErrors).isTrue()
        assertThat(config.captureNetworkRequests).isFalse()
        assertThat(config.captureNavigationEvents).isFalse()
        assertThat(config.captureWebVitals).isFalse()
        assertThat(config.captureLongTasks).isFalse()
        assertThat(config.captureConsoleLogs).isFalse()
        assertThat(config.captureUserInteractions).isFalse()
    }

    @Test
    fun builder_shouldDefaultToNativeOnlyMode() {
        val config = WebViewConfiguration.builder().build()

        assertThat(config.instrumentationMode).isEqualTo(WebViewInstrumentationMode.NATIVE_ONLY)
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

    private fun spyLogger(): LoggerImpl {
        val logger = Capture.logger()
        return spy(logger as LoggerImpl)
    }
}
