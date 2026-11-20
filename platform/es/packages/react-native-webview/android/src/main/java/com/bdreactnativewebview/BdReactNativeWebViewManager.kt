// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package com.bdreactnativewebview

import com.facebook.react.uimanager.ThemedReactContext
import com.reactnativecommunity.webview.RNCWebViewManager
import com.reactnativecommunity.webview.RNCWebViewWrapper
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.webview.WebViewCapture

/** A react-native-webview manager that instruments each WebView before its source is loaded. */
@OptIn(ExperimentalBitdriftApi::class)
class BdReactNativeWebViewManager : RNCWebViewManager() {
  override fun getName(): String = "BdReactNativeWebView"

  override fun createViewInstance(context: ThemedReactContext): RNCWebViewWrapper =
    super.createViewInstance(context).also { wrapper ->
      WebViewCapture.instrument(wrapper.webView)
    }
}
