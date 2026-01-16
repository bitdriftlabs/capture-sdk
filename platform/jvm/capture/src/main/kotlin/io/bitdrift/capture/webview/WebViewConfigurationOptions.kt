// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

/**
 * Configuration options for WebView instrumentation.
 *
 * When provided to [io.bitdrift.capture.Configuration], enables automatic WebView monitoring including
 * Core Web Vitals, page load events, and network activity capture.
 *
 * If `null` is passed to [io.bitdrift.capture.Configuration.webViewConfigurationOptions], WebView monitoring
 * is disabled and any bytecode instrumentation injected by the Gradle plugin will be
 * short-circuited (no-op).
 *
 * @param captureConsoleLog Whether to capture JavaScript console.log/warn/error messages.
 *                          Defaults to true.
 */
data class WebViewConfigurationOptions
    @JvmOverloads
    constructor(
        val captureConsoleLog: Boolean = true,
    )
