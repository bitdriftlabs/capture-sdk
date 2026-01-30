@file:Suppress("UndocumentedPublicClass")
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
 * Defines how WebView instrumentation interacts with JavaScript settings.
 */
enum class WebViewInstrumentationMode {
    /**
     * Uses native WebViewClient callbacks only. No JavaScript required.
     * Captures: page view, navigation, errors, HTTP/SSL errors, renderer crashes.
     */
    NATIVE_ONLY,

    /**
     * Full JavaScript bridge instrumentation. Enables JavaScript if not already enabled.
     */
    JAVASCRIPT_BRIDGE,
}

/**
 * Configuration for WebView instrumentation.
 */
sealed interface WebViewConfiguration {
    /**
     * Native-only instrumentation using WebViewClient callbacks.
     */
    data class NativeOnly(
        /** Enables native page view events. */
        val capturePageViews: Boolean = false,
        /** Enables native navigation events. */
        val captureNavigationEvents: Boolean = false,
        /** Enables native error events (HTTP/SSL/render process). */
        val captureErrors: Boolean = false,
        /** Enables resource load callbacks. */
        val captureResourceLoads: Boolean = false,
    ) : WebViewConfiguration

    /**
     * JavaScript bridge instrumentation. Requires/enables JavaScript.
     */
    data class JavaScriptBridge(
        /** Enables page view events emitted by the JS bridge. */
        val capturePageViews: Boolean = false,
        /** Enables navigation events emitted by the JS bridge. */
        val captureNavigationEvents: Boolean = false,
        /** Enables JS error events. */
        val captureErrors: Boolean = false,
        /** Enables JS network request events. */
        val captureNetworkRequests: Boolean = false,
        /** Enables Web Vitals metrics. */
        val captureWebVitals: Boolean = false,
        /** Enables long task detection. */
        val captureLongTasks: Boolean = false,
        /** Enables console log capture. */
        val captureConsoleLogs: Boolean = false,
        /** Enables user interaction capture. */
        val captureUserInteractions: Boolean = false,
    ) : WebViewConfiguration

    companion object {
        /** Factory methods for common configurations. */
        @JvmStatic
        fun nativeOnly() =
            NativeOnly(
                capturePageViews = true,
                captureNavigationEvents = true,
                captureErrors = true,
            )

        /** Full JavaScript bridge configuration with common defaults enabled. */
        @JvmStatic
        fun javaScriptBridge() =
            JavaScriptBridge(
                capturePageViews = true,
                captureNavigationEvents = true,
                captureErrors = true,
                captureNetworkRequests = true,
                captureWebVitals = true,
                captureLongTasks = true,
                captureConsoleLogs = true,
                captureUserInteractions = true,
            )
    }
}

internal fun WebViewConfiguration?.toFields(): ArrayFields =
    this?.let {
        fieldsOf("_webview_monitoring_configuration" to it.toJson())
    } ?: ArrayFields.EMPTY

internal fun WebViewConfiguration.toJson(): String =
    when (this) {
        is WebViewConfiguration.NativeOnly ->
            JSONObject()
                .apply {
                    put("instrumentationMode", WebViewInstrumentationMode.NATIVE_ONLY.name)
                    put("capturePageViews", capturePageViews)
                    put("captureNavigationEvents", captureNavigationEvents)
                    put("captureErrors", captureErrors)
                    put("captureResourceLoads", captureResourceLoads)
                }.toString()
        is WebViewConfiguration.JavaScriptBridge ->
            JSONObject()
                .apply {
                    put("instrumentationMode", WebViewInstrumentationMode.JAVASCRIPT_BRIDGE.name)
                    put("capturePageViews", capturePageViews)
                    put("captureNavigationEvents", captureNavigationEvents)
                    put("captureErrors", captureErrors)
                    put("captureNetworkRequests", captureNetworkRequests)
                    put("captureWebVitals", captureWebVitals)
                    put("captureLongTasks", captureLongTasks)
                    put("captureConsoleLogs", captureConsoleLogs)
                    put("captureUserInteractions", captureUserInteractions)
                }.toString()
    }
