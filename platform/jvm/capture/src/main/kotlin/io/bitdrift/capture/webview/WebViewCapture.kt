// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LoggerImpl

/**
 * WebView instrumentation for capturing page load events, performance metrics,
 * and network activity from within WebViews.
 *
 * This class injects a JavaScript bridge to capture Core Web Vitals and network requests
 * made from within the web content.
 *
 * Usage:
 * ```kotlin
 * val webView = findViewById<WebView>(R.id.webView)
 * WebViewCapture.instrument(webView)
 * webView.loadUrl("https://example.com")
 * ```
 */
class WebViewCapture(
    private val original: WebViewClient,
    private val logger: ILogger? = Capture.logger(),
) : WebViewClient() {
    private var bridgeReady = false

    private fun getLogger(): ILogger? = logger ?: Capture.logger()

    /**
     * Marks the bridge as ready. Called from the JavaScript bridge handler.
     */
    internal fun onBridgeReady() {
        bridgeReady = true
    }

    /**
     * Companion object for WebViewCapture.
     */
    companion object {
        private const val TAG = "WebViewCapture"
        private const val BRIDGE_NAME = "BitdriftLogger"

        /**
         * Instruments a WebView to capture Core Web Vitals and network requests.
         *
         * This method:
         * - Enables JavaScript execution
         * - Injects the Bitdrift JavaScript bridge at document start
         * - Registers a native interface for receiving bridge messages
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

            // Wrap existing WebViewClient
            val capture =
                if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
                    val original = WebViewCompat.getWebViewClient(webview)
                    WebViewCapture(original, effectiveLogger)
                } else {
                    WebViewCapture(WebViewClient(), effectiveLogger)
                }

            webview.webViewClient = capture

            // Enable JavaScript (required for bridge)
            webview.settings.javaScriptEnabled = true

            // Register JavaScript interface for receiving bridge messages
            val bridgeHandler = WebViewBridgeHandler(loggerImpl, effectiveLogger, capture)
            webview.addJavascriptInterface(bridgeHandler, BRIDGE_NAME)

            // Inject JavaScript at document start for early initialization
            injectScript(webview, effectiveLogger)
        }

        @SuppressLint("RequiresFeature")
        private fun injectScript(
            webview: WebView,
            logger: ILogger?,
        ) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                // Fall back to evaluateJavascript after page load on older WebViews
                logger?.log(LogLevel.DEBUG, emptyMap()) {
                    "WebView DOCUMENT_START_SCRIPT not supported, using fallback injection"
                }
                return
            }

            try {
                val script = WebViewBridgeScript.SCRIPT
                WebViewCompat.addDocumentStartJavaScript(
                    webview,
                    script,
                    setOf("*"), // Apply to all frames
                )
            } catch (e: Exception) {
                logger?.log(LogLevel.WARNING, mapOf("_error" to (e.message ?: ""))) {
                    "Failed to inject WebView bridge script"
                }
            }
        }
    }
}

/**
 * JavaScript interface that receives messages from the injected bridge script.
 */
internal class WebViewBridgeHandler(
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
