// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi

/**
 * WebView instrumentation for capturing page load events, performance metrics,
 * and network activity from within WebViews.
 *
 * This class injects a JavaScript bridge to capture Core Web Vitals and network requests
 * made from within the web content.
 *
 * Usage:
 * ```kotlin
 * WebViewCapture.instrument(webView)
 * webView.loadUrl("https://example.com")
 * ```
 */
internal class WebViewCapture(
    private val original: WebViewClient,
    private val logger: ILogger? = Capture.logger(),
    private val needsFallbackInjection: Boolean = false,
) : WebViewClient() {
    private var bridgeReady = false
    private var scriptInjected = false

    /**
     * Marks the bridge as ready. Called from the JavaScript bridge handler.
     */
    internal fun onBridgeReady() {
        bridgeReady = true
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        // Reset injection state for new page loads
        if (needsFallbackInjection) {
            scriptInjected = false
        }
        original.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        // Inject script after page load for older WebViews
        if (needsFallbackInjection && !scriptInjected && view != null) {
            injectScriptFallback(view)
            scriptInjected = true
        }
        original.onPageFinished(view, url)
    }

    /**
     * Injects the bridge script using evaluateJavascript for older WebViews.
     * This is a fallback for devices that don't support DOCUMENT_START_SCRIPT.
     * Note: Some early metrics (FCP, TTFB) may be missed with this approach.
     */
    private fun injectScriptFallback(webview: WebView) {
        try {
            val script = WebViewBridgeScript.SCRIPT
            // Wrap in IIFE to avoid polluting global scope and handle re-injection
            val wrappedScript =
                "(function() { if (window.__bitdriftBridgeInjected) return; " +
                    "$script window.__bitdriftBridgeInjected = true; })();"
            webview.evaluateJavascript(wrappedScript, null)
        } catch (e: Exception) {
            logger?.log(LogLevel.WARNING, mapOf("_error" to (e.message ?: ""))) {
                "Failed to inject WebView bridge script via fallback"
            }
        }
    }

    /**
     * Companion object for WebViewCapture.
     */
    internal companion object {
        private const val BRIDGE_NAME = "BitdriftLogger"
        private const val TAG_KEY_INSTRUMENTED = 0x62697464 // "bitd" in hex, unique key for setTag

        private fun isWebkitAvailable(): Boolean = runCatching { Class.forName("androidx.webkit.WebViewFeature") }.isSuccess

        private fun WebView.isAlreadyInstrumented(): Boolean = this.getTag(TAG_KEY_INSTRUMENTED) == true

        private fun WebView.markAsInstrumented() {
            this.setTag(TAG_KEY_INSTRUMENTED, true)
        }

        /**
         * Instruments a WebView to capture Core Web Vitals and network requests.
         *
         * This method is idempotent - calling it multiple times on the same WebView
         * will only instrument it once.
         *
         * This method:
         * - Enables JavaScript execution
         * - Injects the Bitdrift JavaScript bridge at document start
         * - Registers a native interface for receiving bridge messages
         *
         * Note: This method will short-circuit (no-op) if WebView monitoring is not enabled
         * in the Capture configuration. To enable WebView monitoring, provide a
         * [WebViewConfiguration] instance when starting Capture.
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
            val loggerImpl = effectiveLogger as? LoggerImpl ?: return

            @OptIn(ExperimentalBitdriftApi::class)
            if (loggerImpl.webViewConfiguration == null || webview.isAlreadyInstrumented()) {
                return
            }

            if (!isWebkitAvailable()) {
                effectiveLogger.log(LogLevel.WARNING, emptyMap()) {
                    "androidx.webkit not available, WebView instrumentation disabled"
                }
                return
            }

            // Check if we need fallback injection (older WebViews)
            val needsFallback =
                !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

            // Wrap existing WebViewClient
            val capture =
                if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
                    val original = WebViewCompat.getWebViewClient(webview)
                    WebViewCapture(original, effectiveLogger, needsFallback)
                } else {
                    WebViewCapture(WebViewClient(), effectiveLogger, needsFallback)
                }

            webview.webViewClient = capture

            // Enable JavaScript (required for bridge)
            webview.settings.javaScriptEnabled = true

            // Register JavaScript interface for receiving bridge messages
            val bridgeHandler = WebViewBridgeHandler(loggerImpl, effectiveLogger, capture)
            webview.addJavascriptInterface(bridgeHandler, BRIDGE_NAME)

            // Inject JavaScript at document start for early initialization
            injectScript(webview, effectiveLogger)

            webview.markAsInstrumented()
        }

        @SuppressLint("RequiresFeature")
        private fun injectScript(
            webview: WebView,
            logger: ILogger?,
        ) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                // Fallback injection will be handled in onPageFinished
                logger?.log(LogLevel.DEBUG, emptyMap()) {
                    "WebView DOCUMENT_START_SCRIPT not supported, using onPageFinished fallback"
                }
                return
            }

            runCatching {
                val script = WebViewBridgeScript.SCRIPT
                WebViewCompat.addDocumentStartJavaScript(
                    webview,
                    script,
                    setOf("*"), // Apply to all frames
                )
            }.getOrElse { error ->
                logger?.log(LogLevel.WARNING, mapOf("_error" to (error.message ?: ""))) {
                    "Failed to inject WebView bridge script"
                }
            }
        }
    }
}

/**
 * JavaScript interface that receives messages from the injected bridge script.
 */
private class WebViewBridgeHandler(
    loggerImpl: LoggerImpl?,
    private val logger: ILogger?,
    private val capture: WebViewCapture,
) {
    private val messageHandler = WebViewMessageHandler(loggerImpl)

    @JavascriptInterface
    fun log(message: String) {
        try {
            messageHandler.handleMessage(message, capture)
        } catch (e: Exception) {
            logger?.log(LogLevel.WARNING, mapOf("_error" to (e.message ?: ""))) {
                "Failed to handle WebView bridge message"
            }
        }
    }
}
