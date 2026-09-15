// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import org.json.JSONObject

internal data class WebViewScriptConfiguration(
    val capturePageViews: Boolean = true,
    val captureNetworkRequests: Boolean = true,
    val captureNavigationEvents: Boolean = true,
    val captureWebVitals: Boolean = true,
    val captureLongTasks: Boolean = true,
    val captureConsoleLogs: Boolean = true,
    val captureUserInteractions: Boolean = true,
    val captureErrors: Boolean = true,
)

internal fun WebViewScriptConfiguration.toJson(): String =
    JSONObject()
        .apply {
            put("captureConsoleLogs", captureConsoleLogs)
            put("captureErrors", captureErrors)
            put("captureNetworkRequests", captureNetworkRequests)
            put("captureNavigationEvents", captureNavigationEvents)
            put("capturePageViews", capturePageViews)
            put("captureWebVitals", captureWebVitals)
            put("captureLongTasks", captureLongTasks)
            put("captureUserInteractions", captureUserInteractions)
        }.toString()
