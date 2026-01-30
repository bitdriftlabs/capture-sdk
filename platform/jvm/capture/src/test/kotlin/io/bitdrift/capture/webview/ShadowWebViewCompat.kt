// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(WebViewCompat::class, isInAndroidSdk = false)
object ShadowWebViewCompat {
    @Implementation
    @JvmStatic
    fun addDocumentStartJavaScript(
        @Suppress("UNUSED_PARAMETER") webView: WebView,
        @Suppress("UNUSED_PARAMETER") script: String,
        @Suppress("UNUSED_PARAMETER") allowedOriginRules: Set<String>,
    ) {
        // no-op
    }
}
