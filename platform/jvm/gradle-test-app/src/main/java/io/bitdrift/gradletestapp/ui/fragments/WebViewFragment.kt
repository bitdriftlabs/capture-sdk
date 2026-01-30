// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import io.bitdrift.gradletestapp.R

/**
 * A basic WebView that can be used to test multi process.
 * See AndroidManifest entry with android:name="android.webkit.WebView.Multiprocess"
 */
class WebViewFragment : Fragment() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_web_view, container, false)
        val webView = view.findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        val url = arguments?.getString(ARG_URL) ?: WEBVIEW_URLS.first().second
        webView.loadUrl(url)
        return view
    }

    companion object {
        const val ARG_URL = "url"

        /**
         * List of URLs available for WebView testing.
         * Pair of (display name, URL)
         */
        val WEBVIEW_URLS = listOf(
            "SDK Test Page" to "file:///android_asset/test-page/index.html",
            "bitdrift.io" to "https://bitdrift.io/",
            "bitdrift.io/hello (404)" to "https://bitdrift.io/hello",
            "bitdrift.ai (timeout)" to "https://bitdrift.ai/",
            "Wikipedia" to "https://www.wikipedia.org/",
        )
    }
}
