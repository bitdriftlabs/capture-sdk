// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import androidx.webkit.WebViewFeature
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(WebViewFeature::class, isInAndroidSdk = false)
@Suppress("detekt:FunctionOnlyReturningConstant")
object ShadowWebViewFeature {
    @Implementation
    @JvmStatic
    fun isFeatureSupported(
        @Suppress("UNUSED_PARAMETER") feature: String,
    ): Boolean = true
}
