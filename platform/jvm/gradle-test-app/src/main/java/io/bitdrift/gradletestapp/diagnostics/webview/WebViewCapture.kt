package io.bitdrift.gradletestapp.diagnostics.webview

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
    private val original : WebViewClient,
    private val logger: ILogger? = Capture.logger(),
) : WebViewClient() {

    // attempts to get the latest logger if one wasn't found at construction time
    private fun getLogger(): ILogger? = logger ?: Capture.logger()
    @Volatile
    var pageLoad: Span? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        val fields = buildMap {
            put("_span_type", "webview")
            url?.let { put("_url", it) }
        }
        pageLoad = getLogger()?.startSpan("pageLoad", LogLevel.INFO, fields)
        original.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        original.onPageFinished(view, url)
        pageLoad?.end(SpanResult.SUCCESS)
        pageLoad = null
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        // Only handle errors for the main page load, not for sub-resources like images.
        if (request?.isForMainFrame == true) {
            val fields = buildMap {
                put("_errorCode", error?.errorCode.toString())
                put("_description", error?.description?.toString().orEmpty())
                put("_url", request.url.toString())
            }

            // End the span with a failure result
            pageLoad?.end(SpanResult.FAILURE, fields)
            pageLoad = null
        }
        original.onReceivedError(view, request, error)
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