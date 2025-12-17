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
import timber.log.Timber

class WebViewCapture(
    private val original: WebViewClient,
    private val logger: ILogger? = Capture.logger(),
) : WebViewClient() {

    // attempts to get the latest logger if one wasn't found at construction time
    private fun getLogger(): ILogger? = logger ?: Capture.logger()

    @Volatile
    var pageLoad: Span? = null

    private fun logLifecycleCallback(methodName: String, url: String? = null) {
        Timber.i("WebViewClient-$methodName(),url=$url")
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        logLifecycleCallback("onLoadResource", url)
        super.onLoadResource(view, url)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        logLifecycleCallback("onPageStarted", url)
        val fields = buildMap {
            url?.let { put("_url", it) }
        }
        pageLoad = getLogger()?.startSpan("webview.pageLoad", LogLevel.INFO, fields)
        original.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        logLifecycleCallback("onPageFinished", url)
        original.onPageFinished(view, url)
        pageLoad?.end(SpanResult.SUCCESS)
        pageLoad = null
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        logLifecycleCallback("onReceivedError", request?.url?.toString())
        // Only handle errors for the main page load, not for sub-resources like images.
        if (request?.isForMainFrame == true) {
            val fields = buildMap {
                put("_errorCode", error?.errorCode.toString())
                put("_errorCodeName", error?.errorCode?.toErrorCodeName().orEmpty())
                put("_description", error?.description?.toString().orEmpty())
                put("_url", request.url.toString())
            }

            // End the span with a failure result
            pageLoad?.end(SpanResult.FAILURE, fields)
            pageLoad = null
        }
        original.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        logLifecycleCallback("onReceivedHttpError", request?.url?.toString())
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

    companion object {
        fun attach(webview: WebView) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
                val original = WebViewCompat.getWebViewClient(webview)
                webview.webViewClient = WebViewCapture(original)
            }
        }
    }
}
