// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.webview

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult

class WebViewCapture(
    private val original: WebViewClient,
    private val logger: ILogger? = Capture.logger(),
) : WebViewClient() {

    // all WebViewClient callbacks are guaranteed to happen on the main thread, so no need for synchronization
    private var pageLoad: Span? = null
    private var ongoingRequest: PageLoadRequest? = null

    // attempts to get the latest logger if one wasn't found at construction time
    private fun getLogger(): ILogger? = logger ?: Capture.logger()

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (ongoingRequest == null) {
            // Only for when onGoingRequest wasn't already set by an early error callback
            ongoingRequest = PageLoadRequest(url.orEmpty())
        }
        pageLoad = getLogger()?.startSpan("webview.pageLoad", LogLevel.DEBUG, ongoingRequest?.fields)

        original.onPageStarted(view, url, favicon)
    }

    // This callback always happens at the end, even after error callbacks
    override fun onPageFinished(view: WebView?, url: String?) {
        ongoingRequest?.let {
            if (it.isError) {
                pageLoad?.end(SpanResult.FAILURE, it.fields)
            } else {
                pageLoad?.end(SpanResult.SUCCESS)
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
        // Only handle errors for the main page load, not for sub-resources like images.
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

    // This callback can happen very early; even before onPageStarted
    // see: https://issuetracker.google.com/issues/210920403
    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        // Only handle errors for the main page load, not for sub-resources like images.
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
        fun instrument(webview: WebView) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
                val original = WebViewCompat.getWebViewClient(webview)
                webview.webViewClient = WebViewCapture(original)
            }
        }
    }
}
