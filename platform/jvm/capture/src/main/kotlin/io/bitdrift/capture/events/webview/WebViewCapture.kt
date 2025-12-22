package io.bitdrift.capture.events.webview

import android.graphics.Bitmap
import android.util.Log
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

/**
 * A [WebViewClient] that wraps an existing [WebViewClient] to automatically capture
 * events and metrics related to page loads in a [WebView].
 *
 * This class intercepts key callbacks from the [WebViewClient] lifecycle to create a [Span]
 * that measures the duration of a page load. It records whether the load was successful or
 * resulted in an error, and attaches relevant details such as the URL and any error information.
 *
 * It is designed to be a transparent wrapper. All intercepted callbacks are forwarded to the
 * original [WebViewClient] instance after the capture logic is executed, ensuring that existing
 * functionality is not broken.
 *
 * Use the [WebViewCapture.instrument] companion object function to easily apply this to an
 * existing [WebView] instance.
 *
 * @property original The original [WebViewClient] that was set on the [WebView]. All callback
 * events are forwarded to this client after being processed by [WebViewCapture].
 * @property logger An optional [ILogger] instance used for logging spans. If not provided, it
 * will attempt to retrieve the default logger from [Capture.logger].
 */
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

    /**
     * Provides a factory method for instrumenting a [WebView].
     */
    companion object {
        /**
         * Instruments a [WebView] to capture page load events.
         *
         * This function wraps the existing [WebViewClient] of the given [webview] with a [WebViewCapture]
         * instance. This allows for the interception of page load lifecycle callbacks (`onPageStarted`,
         * `onPageFinished`, `onReceivedError`, etc.) to create spans that measure page load performance
         * and capture errors.
         *
         * If the WebView has already been instrumented with `WebViewCapture`, this function will do nothing
         * and return the original WebView. It also checks for the required `WebViewFeature.GET_WEB_VIEW_CLIENT`
         * and will not instrument if the feature is unsupported on the device.
         *
         * @param webview The [WebView] instance to instrument.
         * @return The instrumented [WebView] instance.
         */
        @JvmStatic
        fun instrument(webview: WebView): WebView {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
                Log.i("miguel", "WebViewCapture.instrument(): WebView client not supported, skipping instrumentation")
                return webview
            }
            // noinspection RequiresFeature
            val original = WebViewCompat.getWebViewClient(webview)
            if (original is WebViewCapture) {
                Log.i("miguel", "WebViewCapture.instrument(): WebView already instrumented, skipping instrumentation")
                return webview
            }
            webview.webViewClient = WebViewCapture(original)
            Log.i("miguel", "WebViewCapture.instrument(): WebView instrumented with WebViewCapture successfully")
            return webview
        }
    }
}
