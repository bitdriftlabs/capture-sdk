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
 * **Note:** This configuration only takes effect when the Gradle plugin has
 * `automaticWebViewInstrumentation = true` enabled. Without the plugin instrumentation,
 * this configuration has no effect.
 *
 * When provided to [io.bitdrift.capture.Configuration], enables automatic WebView monitoring including
 * Core Web Vitals, page load events, and network activity capture.
 *
 * If `null` is passed to [io.bitdrift.capture.Configuration.webViewConfiguration], WebView monitoring
 * is disabled and any bytecode instrumentation injected by the Gradle plugin will be
 * short-circuited (no-op).
 *
 * @param captureConsoleLog Whether to capture JavaScript console.log/warn/error messages.
 *                          Defaults to true.
 */
data class WebViewConfiguration
    @JvmOverloads
    constructor(
        /**
         * TODO: BIT-7217. Implement captureConsoleLog and ignore urls patterns
         */
        val captureConsoleLog: Boolean = true,
    )
