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