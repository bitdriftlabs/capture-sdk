// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

/**
 * Configuration for WebView instrumentation.
 *
 * **Note:** In order for this configuration to take effect, the `io.bitdrift.capture-plugin`
 * Gradle plugin must be applied to your project and have `automaticWebViewInstrumentation = true`
 * enabled. Without the plugin, this configuration has no effect.
 *
 * When provided to [io.bitdrift.capture.Configuration], enables automatic WebView monitoring including
 * Core Web Vitals, page load events, and network activity capture.
 *
 * If `null` is passed to [io.bitdrift.capture.Configuration.webViewConfiguration], WebView monitoring
 * is disabled and any bytecode instrumentation injected by the Gradle plugin will be
 * short-circuited (no-op).
 *
 * @param enablePageViews Whether to capture page view tracking events. Defaults to false.
 * @param enableNetworkRequests Whether to capture network requests made from the WebView. Defaults to false.
 * @param enableNavigationEvents Whether to capture navigation events. Defaults to false.
 * @param enableWebVitals Whether to capture Core Web Vitals (LCP, FCP, CLS, INP, TTFB). Defaults to false.
 * @param enableLongTasks Whether to capture long tasks that block the main thread. Defaults to false.
 * @param enableConsoleLogs Whether to capture JavaScript console.log/warn/error messages. Defaults to false.
 * @param enableUserInteractions Whether to capture user interactions (clicks, rage clicks, etc.). Defaults to false.
 * @param enableErrors Whether to capture JavaScript errors, promise rejections, and resource errors. Defaults to false.
 */
data class WebViewConfiguration
    @JvmOverloads
    constructor(
        val enablePageViews: Boolean = false,
        val enableNetworkRequests: Boolean = false,
        val enableNavigationEvents: Boolean = false,
        val enableWebVitals: Boolean = false,
        val enableLongTasks: Boolean = false,
        val enableConsoleLogs: Boolean = false,
        val enableUserInteractions: Boolean = false,
        val enableErrors: Boolean = false,
    )
