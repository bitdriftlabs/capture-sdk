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
    fun instrument_withJavaScriptBridge_shouldEnableJavascriptAndLogSuccess() {
        startSdk(webViewConfiguration = WebViewConfiguration.javaScriptBridge())
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
    fun instrument_withJavaScriptBridge_shouldEnableJavascript() {
        val config = WebViewConfiguration.JavaScriptBridge(
            capturePageViews = true,
        )
        startSdk(webViewConfiguration = config)

        WebViewCapture.instrument(webView)

        assertThat(webView.settings.javaScriptEnabled).isTrue()
    }

    @Test
    fun instrument_withNativeOnlyMode_shouldNotEnableJavascript() {
        val config = WebViewConfiguration.nativeOnly()
        startSdk(webViewConfiguration = config)

        WebViewCapture.instrument(webView)

        assertThat(webView.settings.javaScriptEnabled).isFalse()
    }

    @Test
    fun instrument_withNativeOnlyMode_shouldWrapExistingWebViewClient() {
        val config = WebViewConfiguration.nativeOnly()
        startSdk(webViewConfiguration = config)

        WebViewCapture.instrument(webView)

        assertThat(webView.settings.javaScriptEnabled).isFalse()
    }

    @Test
    fun instrument_calledTwice_shouldOnlyInstrumentOnce() {
        val config = WebViewConfiguration.javaScriptBridge()
        startSdk(webViewConfiguration = config)

        WebViewCapture.instrument(webView)
        val firstJavaScriptState = webView.settings.javaScriptEnabled

        WebViewCapture.instrument(webView)
        val secondJavaScriptState = webView.settings.javaScriptEnabled

        assertThat(firstJavaScriptState).isTrue()
        assertThat(secondJavaScriptState).isTrue()
    }

    @Test
    fun javaScriptBridge_factoryMethod_shouldHaveCorrectDefaults() {
        val config = WebViewConfiguration.javaScriptBridge()

        assertThat(config).isInstanceOf(WebViewConfiguration.JavaScriptBridge::class.java)
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

        assertThat(config).isInstanceOf(WebViewConfiguration.NativeOnly::class.java)
        assertThat(config.capturePageViews).isTrue()
        assertThat(config.captureErrors).isTrue()
        assertThat(config.captureNavigationEvents).isTrue()
        assertThat(config.captureResourceLoads).isFalse()
    }

    @Test
    fun nativeOnly_defaultConstructor_shouldDefaultToNativeOnlyMode() {
        val config = WebViewConfiguration.NativeOnly()

        assertThat(config).isInstanceOf(WebViewConfiguration.NativeOnly::class.java)
    }

    private fun startSdk(webViewConfiguration: WebViewConfiguration? = null) {
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
