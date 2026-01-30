// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.fieldsOf

/**
 * WebView instrumentation for capturing page load events, performance metrics,
 * and network activity from within WebViews.
 *
 * Supports two instrumentation modes configured via [WebViewConfiguration]:
 *
 * - **NativeOnly**: Uses native WebViewClient callbacks. No JavaScript enabled is required.
 *
 * - **JavaScriptBridge**: Injects a JavaScript bridge for rich telemetry including
 *   Web Vitals, console logs, JS errors, user interactions. Enables JavaScript if needed.
 *
 * Usage:
 * ```kotlin
 * WebViewCapture.instrument(webView)
 * webView.loadUrl("https://example.com")
 * ```
 *
 * **Note:** WebViewCapture.instrument(webView) will be called automatically only if the
 * `io.bitdrift.capture-plugin` Gradle plugin is applied with `automaticWebViewInstrumentation`
 * set to `true`.
 */
internal object WebViewCapture {
    private const val BRIDGE_NAME = "BitdriftLogger"
    private const val TAG_KEY_INSTRUMENTED = 0x62697464 // "bitd" in hex, unique key for setTag

     /**
      * Instruments a WebView based on the configured [WebViewConfiguration].
     *
     * This method is idempotent - calling it multiple times on the same WebView
     * will only instrument it once.
     *
     * Requirements:
     * - The Bitdrift SDK must be initialized before calling this method
     * - WebView monitoring must be enabled in the Capture configuration
      * - For JavaScriptBridge mode: androidx.webkit library must be available
     *
     * @param webview The WebView to instrument
     * @param logger Optional logger instance. If null, uses Capture.logger()
     */
    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    @JvmStatic
    @JvmOverloads
    fun instrument(
        webview: WebView,
        logger: ILogger? = null,
    ) {
        val effectiveLogger = logger ?: Capture.logger()

        val loggerImpl = effectiveLogger as? LoggerImpl
        if (loggerImpl == null) {
            Log.w(
                LOG_TAG,
                "WebView instrumentation skipped: SDK still not initialized. " +
                    "Call Capture.Logger.start() before instrumenting WebViews.",
            )
            return
        }

        @OptIn(ExperimentalBitdriftApi::class)
        val webViewConfig = loggerImpl.webViewConfiguration
        if (webViewConfig == null) {
            effectiveLogger.logInstrumentationNotInitialized("WebViewConfiguration not provided")
            return
        }

        if (webview.isAlreadyInstrumented()) {
            return
        }

        when (webViewConfig) {
            is WebViewConfiguration.NativeOnly -> {
                instrumentNativeOnly(webview, loggerImpl, webViewConfig)
            }
            is WebViewConfiguration.JavaScriptBridge -> {
                val notSupportedReason = getNotSupportedReason()
                if (notSupportedReason != null) {
                    effectiveLogger.logInstrumentationNotInitialized(notSupportedReason)
                    return
                }
                instrumentWithJavaScript(webview, loggerImpl, webViewConfig)
            }
        }

        webview.markAsInstrumented()
    }

    @SuppressLint("RequiresFeature")
    private fun instrumentNativeOnly(
        webview: WebView,
        logger: IInternalLogger,
        config: WebViewConfiguration.NativeOnly,
    ) {
        val existingClient = WebViewCompat.getWebViewClient(webview)
        webview.webViewClient = NativeWebViewClient(existingClient, logger, config)
        logger.logInternal(
            LogType.INTERNALSDK,
            LogLevel.DEBUG,
            fieldsOf("_mode" to "native"),
        ) {
            "WebView instrumented with native callbacks"
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun instrumentWithJavaScript(
        webview: WebView,
        effectiveLogger: IInternalLogger,
        webViewConfig: WebViewConfiguration.JavaScriptBridge,
    ) {
        webview.settings.javaScriptEnabled = true

        val bridgeHandler = WebViewBridgeMessageHandler(effectiveLogger)
        webview.addJavascriptInterface(bridgeHandler, BRIDGE_NAME)

        injectScript(webview, effectiveLogger, webViewConfig)

        effectiveLogger.logInternal(
            LogType.INTERNALSDK,
            LogLevel.DEBUG,
            fieldsOf("_mode" to "javascript"),
        ) {
            "WebView instrumented with JavaScript bridge"
        }
    }

    private fun isWebkitAvailable(): Boolean =
        runCatching {
            Class.forName("androidx.webkit.WebViewFeature")
        }.isSuccess

    private fun WebView.isAlreadyInstrumented(): Boolean = getTag(TAG_KEY_INSTRUMENTED) == true

    private fun WebView.markAsInstrumented() = setTag(TAG_KEY_INSTRUMENTED, true)

    private fun ILogger.logInstrumentationNotInitialized(reason: String) {
        log(
            LogLevel.WARNING,
            fieldsOf(
                "reason" to reason,
                "_source" to "webview",
            ),
        ) {
            "webview.notInitialized"
        }
    }

    private fun getNotSupportedReason(): String? =
        if (!isWebkitAvailable()) {
            "androidx.webkit not available"
        } else if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            "WebViewFeature.DOCUMENT_START_SCRIPT not supported"
        } else if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
            "WebViewFeature.GET_WEB_VIEW_CLIENT not supported"
        } else {
            null
        }

    @SuppressLint("RequiresFeature")
    private fun injectScript(
        webview: WebView,
        logger: IInternalLogger?,
        config: WebViewConfiguration.JavaScriptBridge,
    ) {
        runCatching {
            val script = WebViewBridgeScript.getScript(config)
            WebViewCompat.addDocumentStartJavaScript(
                webview,
                script,
                setOf("*"), // Apply to all frames
            )
            logger?.logInternal(type = LogType.INTERNALSDK, LogLevel.DEBUG, ArrayFields.EMPTY) {
                "WebView bridge script injected successfully"
            }
        }.getOrElse { error ->
            logger?.log(LogLevel.WARNING, fieldsOf("_error" to (error.message ?: ""))) {
                "Failed to inject WebView bridge script"
            }
        }
    }
}
