// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.fieldsOf
import org.json.JSONObject

/**
 * Configuration for WebView instrumentation.
 *
 * **Note:** In order for this configuration to take effect, the `io.bitdrift.capture-plugin`
 * Gradle plugin must be applied to your project with `automaticWebViewInstrumentation` set to `true`.
 * Without the plugin, this configuration has no effect.
 *
 * When provided to [io.bitdrift.capture.Configuration], enables automatic WebView monitoring including
 * Core Web Vitals, page load events, and network activity capture.
 *
 * If `null` is passed to [io.bitdrift.capture.Configuration.webViewConfiguration], WebView monitoring
 * is disabled and any bytecode instrumentation injected by the Gradle plugin will be
 * short-circuited (no-op).
 *
 * @param capturePageViews Whether to capture page view tracking events. Defaults to false.
 * @param captureNetworkRequests Whether to capture network requests made from the WebView. Defaults to false.
 * @param captureNavigationEvents Whether to capture navigation events. Defaults to false.
 * @param captureWebVitals Whether to capture Core Web Vitals (LCP, FCP, CLS, INP, TTFB). Defaults to false.
 * @param captureLongTasks Whether to capture long tasks that block the main thread. Defaults to false.
 * @param captureConsoleLogs Whether to capture JavaScript console.log/warn/error messages. Defaults to false.
 * @param captureUserInteractions Whether to capture user interactions (clicks, rage clicks, etc.). Defaults to false.
 * @param captureErrors Whether to capture JavaScript errors, promise rejections, and resource errors. Defaults to false.
 */
data class WebViewConfiguration
    @JvmOverloads
    constructor(
        val capturePageViews: Boolean = false,
        val captureNetworkRequests: Boolean = false,
        val captureNavigationEvents: Boolean = false,
        val captureWebVitals: Boolean = false,
        val captureLongTasks: Boolean = false,
        val captureConsoleLogs: Boolean = false,
        val captureUserInteractions: Boolean = false,
        val captureErrors: Boolean = false,
    )

internal fun WebViewConfiguration.toLogFields(): ArrayFields = fieldsOf("_webview_monitoring_enabled" to this.toJson())

internal fun WebViewConfiguration.toJson(): String =
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
