// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.webview.WebViewCapture
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.ui.webview.CustomWebView

/**
 * A basic WebView that can be used to test multi process.
 * See AndroidManifest entry with android:name="android.webkit.WebView.Multiprocess"
 */
class WebViewFragment : Fragment() {

    @OptIn(ExperimentalBitdriftApi::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_web_view, container, false)
        val webView = view.findViewById<CustomWebView>(R.id.webView)
        webView.webViewClient = WebViewClient()
        val demoKey = arguments?.getString(ARG_DEMO_KEY) ?: TEST_PAGE
        val demo = WEBVIEW_DEMOS[demoKey] ?: WEBVIEW_DEMOS.getValue(TEST_PAGE)
        if (demo.enableJavaScript) {
            webView.settings.javaScriptEnabled = true
        }
        if (demo.instrumentManually) {
            WebViewCapture.instrument(webView)
        }
        webView.loadUrl(demo.url)
        return view
    }

    companion object {
        const val ARG_DEMO_KEY = "demo_key"
        const val TEST_PAGE = "test_page"
        const val FULL_AUTOMATIC = "full_automatic"
        const val JAVASCRIPT_ENABLED = "javascript_enabled"
        const val MANUAL = "manual"

        val WEBVIEW_DEMOS = linkedMapOf(
            TEST_PAGE to DemoWebView("Test Web Page - No JavaScript", "file:///android_asset/test-page/index.html"),
            FULL_AUTOMATIC to
                DemoWebView(
                    buttonName = "Android Developers - No JavaScript",
                    url = "https://developer.android.com/",
                ),
            JAVASCRIPT_ENABLED to
                DemoWebView(
                    buttonName = "Android Developers - With JavaScript",
                    url = "https://developer.android.com/",
                    enableJavaScript = true,
                ),
            MANUAL to
                DemoWebView(
                    buttonName = "bitdrift.io - Manual Instrumentation",
                    url = "https://bitdrift.io/",
                    instrumentManually = true,
                ),
            "bitdrift" to DemoWebView("bitdrift.io - No JavaScript", "https://bitdrift.io/"),
            "bitdrift_404" to DemoWebView("bitdrift.io/hello (404) - No JavaScript", "https://bitdrift.io/hello"),
            "bitdrift_timeout" to DemoWebView("bitdrift.ai (timeout) - No JavaScript", "https://bitdrift.ai/"),
            "wikipedia" to DemoWebView("Wikipedia - No JavaScript", "https://www.wikipedia.org/"),
        )
    }

    data class DemoWebView(
        val buttonName: String,
        val url: String,
        val enableJavaScript: Boolean = false,
        val instrumentManually: Boolean = false,
    ) {
        val hasJavaScript: Boolean
            get() = enableJavaScript || instrumentManually
    }
}
