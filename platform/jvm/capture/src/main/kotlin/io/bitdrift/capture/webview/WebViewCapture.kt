// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult

/**
 * WebView instrumentation for capturing page load events, performance metrics,
 * and network activity from within WebViews.
 *
 * This class wraps an existing WebViewClient to intercept page load events and
 * injects a JavaScript bridge to capture Core Web Vitals and network requests
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

    // All WebViewClient callbacks are guaranteed to happen on the main thread
    private var pageLoad: Span? = null
    private var ongoingRequest: PageLoadRequest? = null
    private var bridgeReady = false

    private fun getLogger(): ILogger? = logger ?: Capture.logger()

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (ongoingRequest == null) {
            ongoingRequest = PageLoadRequest(url.orEmpty())
        }
        pageLoad = getLogger()?.startSpan("webview.pageLoad", LogLevel.DEBUG, ongoingRequest?.fields)
        bridgeReady = false

        original.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        ongoingRequest?.let {
            if (it.isError) {
                pageLoad?.end(SpanResult.FAILURE, it.fields)
            } else {
                pageLoad?.end(SpanResult.SUCCESS)
            }
        }
        
        // Log if bridge was not ready before page finished (late injection)
        if (!bridgeReady) {
            getLogger()?.log(LogLevel.WARNING, mapOf("_url" to (url ?: ""))) {
                "WebView bridge not ready before page finished"
            }
        }
        
        pageLoad = null
        ongoingRequest = null

        original.onPageFinished(view, url)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        if (request?.isForMainFrame == true) {
            ongoingRequest = PageLoadRequest(
                url = request.url.toString(),
                isError = true,
                errorCode = error?.errorCode.toString(),
                errorCodeName = error?.errorCode?.toErrorCodeName(),
                errorDescription = error?.description?.toString(),
            )
        }
        original.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        if (request?.isForMainFrame == true) {
            ongoingRequest = PageLoadRequest(
                url = request.url.toString(),
                isError = true,
                errorCode = errorResponse?.statusCode.toString(),
                errorDescription = errorResponse?.reasonPhrase,
            )
        }
        super.onReceivedHttpError(view, request, errorResponse)
    }

    /**
     * Marks the bridge as ready. Called from the JavaScript bridge handler.
     */
    internal fun onBridgeReady() {
        bridgeReady = true
    }

    private fun Int.toErrorCodeName(): String =
        when (this) {
            ERROR_UNKNOWN                 -> "UNKNOWN"
            ERROR_HOST_LOOKUP             -> "HOST_LOOKUP"
            ERROR_UNSUPPORTED_AUTH_SCHEME -> "UNSUPPORTED_AUTH_SCHEME"
            ERROR_AUTHENTICATION          -> "AUTHENTICATION"
            ERROR_PROXY_AUTHENTICATION    -> "PROXY_AUTHENTICATION"
            ERROR_CONNECT                 -> "CONNECT"
            ERROR_IO                      -> "IO"
            ERROR_TIMEOUT                 -> "TIMEOUT"
            ERROR_REDIRECT_LOOP           -> "REDIRECT_LOOP"
            ERROR_UNSUPPORTED_SCHEME      -> "UNSUPPORTED_SCHEME"
            ERROR_FAILED_SSL_HANDSHAKE    -> "FAILED_SSL_HANDSHAKE"
            ERROR_BAD_URL                 -> "BAD_URL"
            ERROR_FILE                    -> "FILE"
            ERROR_FILE_NOT_FOUND          -> "FILE_NOT_FOUND"
            ERROR_TOO_MANY_REQUESTS       -> "TOO_MANY_REQUESTS"
            ERROR_UNSAFE_RESOURCE         -> "UNSAFE_RESOURCE"
            else                          -> "UNKNOWN"
        }

    private data class PageLoadRequest(
        val url: String,
        val isError: Boolean = false,
        val errorCode: String? = null,
        val errorCodeName: String? = null,
        val errorDescription: String? = null,
    ) {
        val fields: Map<String, String> by lazy {
            buildMap {
                put("_url", url)
                if (isError) {
                    errorCode?.let { put("_errorCode", it) }
                    errorCodeName?.let { put("_errorCodeName", it) }
                    errorDescription?.let { put("_description", it) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "WebViewCapture"
        private const val BRIDGE_NAME = "BitdriftLogger"

        /**
         * Instruments a WebView to capture page load events, Core Web Vitals,
         * and network requests.
         *
         * This method:
         * - Wraps the existing WebViewClient to capture page load events
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
        fun instrument(webview: WebView, logger: ILogger? = null) {
            val effectiveLogger = logger ?: Capture.logger()
            
            // Wrap existing WebViewClient
            val capture = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
                val original = WebViewCompat.getWebViewClient(webview)
                WebViewCapture(original, effectiveLogger)
            } else {
                WebViewCapture(WebViewClient(), effectiveLogger)
            }
            
            webview.webViewClient = capture

            // Enable JavaScript (required for bridge)
            webview.settings.javaScriptEnabled = true

            // Register JavaScript interface for receiving bridge messages
            val bridgeHandler = WebViewBridgeHandler(effectiveLogger, capture)
            webview.addJavascriptInterface(bridgeHandler, BRIDGE_NAME)

            // Inject JavaScript at document start for early initialization
            injectScript(webview, effectiveLogger)
        }

        @SuppressLint("RequiresFeature")
        private fun injectScript(webview: WebView, logger: ILogger?) {
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
                    setOf("*") // Apply to all frames
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
    private val logger: ILogger?,
    private val capture: WebViewCapture,
) {
    private val messageHandler = WebViewMessageHandler(logger)

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
