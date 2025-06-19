// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import io.bitdrift.capture.Capture
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.SpanResult

/**
 * A basic WebView that can be used to test multi process.
 * See AndroidManifest entry with android:name="android.webkit.WebView.Multiprocess"
 */
class WebViewFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val span = Capture.Logger.startSpan("WebViewFragment", LogLevel.INFO)
        val view = inflater.inflate(R.layout.fragment_web_view, container, false)
        val webView = view.findViewById<WebView>(R.id.webView)
        webView.loadUrl("https://bitdrift.io/")
        span?.end(SpanResult.SUCCESS)
        return view
    }
}