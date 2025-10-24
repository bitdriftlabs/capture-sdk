// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.bitdrift.capture.Capture
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.gradletestapp.R
import kotlin.concurrent.Volatile

/**
 * A basic WebView that can be used to test multi process.
 * See AndroidManifest entry with android:name="android.webkit.WebView.Multiprocess"
 */
class WebViewFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_web_view, container, false)
        val webView = view.findViewById<WebView>(R.id.webView)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_CLIENT)) {
            val original = WebViewCompat.getWebViewClient(webView)
            webView.webViewClient = WebViewClientWrapper(original)
        }
        webView.loadUrl("https://bitdrift.io/")
        return view
    }

    class WebViewClientWrapper(private val original : WebViewClient) : WebViewClient() {
        @Volatile
        var onPageSpan: Span? = null
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            val fields = url?.let {
                mapOf("_url" to it)
            }
            onPageSpan = Capture.Logger.startSpan("WebViewFragment.onPage", LogLevel.INFO, fields)
            original.onPageStarted(view, url, favicon)
        }
        override fun onPageFinished(view: WebView?, url: String?) {
            original.onPageFinished(view, url)
            onPageSpan?.end(SpanResult.SUCCESS)
            onPageSpan = null
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            // Only handle errors for the main page load, not for sub-resources like images.
            if (request?.isForMainFrame == true) {
                val fields = mapOf(
                    "_errorCode" to error?.errorCode.toString(),
                    "_description" to error?.description?.toString().orEmpty(),
                    "_url" to request.url.toString(),
                )
                Capture.Logger.logError(fields = fields) { "WebViewFragment.onReceivedError" }

                // End the span with a failure result
                onPageSpan?.end(SpanResult.FAILURE)
                onPageSpan = null
            }
            original.onReceivedError(view, request, error)
        }
    }
}
