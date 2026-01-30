// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.util.Log
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.fieldsOf
import org.json.JSONObject

/**
 * Defines how WebView instrumentation interacts with JavaScript settings.
 */
enum class WebViewInstrumentationMode {
    /**
     * Uses native WebViewClient callbacks only. No JavaScript required.
     * Captures: page load start/finish, HTTP errors, SSL errors.
     * Does NOT capture: Web Vitals, console logs, JS errors, user interactions, long tasks.
     *
     * This is the safest mode for security-sensitive applications
     */
    NATIVE_ONLY,

    /**
     * Full JavaScript bridge instrumentation. Enables JavaScript if not already enabled.
     *
     * **Security Warning:** This mode will enable JavaScript on WebViews that have it disabled.
     * Use with caution.
     *
     * This mode provides the richest data including Web Vitals, console logs, JS errors,
     * user interactions, long tasks, and detailed network timing.
     */
    FULL_WITH_JAVASCRIPT,
}

/**
 * Configuration for WebView instrumentation.
 *
 * **Note:** In order for this configuration to take effect, the `io.bitdrift.capture-plugin`
 * Gradle plugin must be applied to your project with `automaticWebViewInstrumentation` set to `true`.
 * Without the plugin, this configuration has no effect.
 *
 * When provided to [io.bitdrift.capture.Configuration], enables automatic WebView monitoring.
 * The level of monitoring depends on the [instrumentationMode]:
 *
 * - [WebViewInstrumentationMode.NATIVE_ONLY]: Basic monitoring via WebViewClient callbacks.
 *   No JavaScript required, safest for security-sensitive apps.
 *
 * - [WebViewInstrumentationMode.FULL_WITH_JAVASCRIPT]: Full monitoring including Web Vitals,
 *   console logs, and user interactions. Requires/enables JavaScript.
 *
 * If `null` is passed to [io.bitdrift.capture.Configuration.webViewConfiguration], WebView monitoring
 * is disabled and any bytecode instrumentation injected by the Gradle plugin will be
 * short-circuited (no-op).
 *
 * Use [Builder] to create instances with fine-grained control, or use convenience methods
 * [nativeOnly] and [fullWithJavaScript] for common configurations.
 *
 * @property instrumentationMode The instrumentation mode determining JavaScript behavior.
 * @property capturePageViews Whether to capture page view tracking events.
 * @property captureNetworkRequests Whether to capture network requests made from the WebView.
 * @property captureNavigationEvents Whether to capture navigation events.
 * @property captureWebVitals Whether to capture Core Web Vitals (LCP, FCP, CLS, INP, TTFB). Requires JavaScript.
 * @property captureLongTasks Whether to capture long tasks that block the main thread. Requires JavaScript.
 * @property captureConsoleLogs Whether to capture JavaScript console messages. Requires JavaScript.
 * @property captureUserInteractions Whether to capture user interactions. Requires JavaScript.
 * @property captureErrors Whether to capture errors (JS errors in FULL mode, HTTP/SSL errors in NATIVE mode).
 */
data class WebViewConfiguration internal constructor(
    val instrumentationMode: WebViewInstrumentationMode,
    val capturePageViews: Boolean,
    val captureNetworkRequests: Boolean,
    val captureNavigationEvents: Boolean,
    val captureWebVitals: Boolean,
    val captureLongTasks: Boolean,
    val captureConsoleLogs: Boolean,
    val captureUserInteractions: Boolean,
    val captureErrors: Boolean,
) {
    @Deprecated(
        message = "Use WebViewConfiguration.builder() or WebViewConfiguration.nativeOnly()/fullWithJavaScript() instead",
        replaceWith = ReplaceWith("WebViewConfiguration.builder().build()"),
    )
    @JvmOverloads
    constructor(
        capturePageViews: Boolean = false,
        captureNetworkRequests: Boolean = false,
        captureNavigationEvents: Boolean = false,
        captureWebVitals: Boolean = false,
        captureLongTasks: Boolean = false,
        captureConsoleLogs: Boolean = false,
        captureUserInteractions: Boolean = false,
        captureErrors: Boolean = false,
    ) : this(
        instrumentationMode = WebViewInstrumentationMode.FULL_WITH_JAVASCRIPT,
        capturePageViews = capturePageViews,
        captureNetworkRequests = captureNetworkRequests,
        captureNavigationEvents = captureNavigationEvents,
        captureWebVitals = captureWebVitals,
        captureLongTasks = captureLongTasks,
        captureConsoleLogs = captureConsoleLogs,
        captureUserInteractions = captureUserInteractions,
        captureErrors = captureErrors,
    )

    /**
     * Builder for creating [WebViewConfiguration] instances with fine-grained control.
     */
    class Builder {
        private var instrumentationMode: WebViewInstrumentationMode = WebViewInstrumentationMode.NATIVE_ONLY
        private var capturePageViews: Boolean = false
        private var captureNetworkRequests: Boolean = false
        private var captureNavigationEvents: Boolean = false
        private var captureWebVitals: Boolean = false
        private var captureLongTasks: Boolean = false
        private var captureConsoleLogs: Boolean = false
        private var captureUserInteractions: Boolean = false
        private var captureErrors: Boolean = false

        /** Sets the instrumentation mode. */
        fun instrumentationMode(mode: WebViewInstrumentationMode) =
            apply {
                this.instrumentationMode = mode
            }

        /** Enables or disables page view capture. */
        fun capturePageViews(enabled: Boolean) =
            apply {
                this.capturePageViews = enabled
            }

        /** Enables or disables network request capture. */
        fun captureNetworkRequests(enabled: Boolean) =
            apply {
                this.captureNetworkRequests = enabled
            }

        /** Enables or disables navigation event capture. */
        fun captureNavigationEvents(enabled: Boolean) =
            apply {
                this.captureNavigationEvents = enabled
            }

        /** Enables or disables Web Vitals capture. Requires JavaScript mode. */
        fun captureWebVitals(enabled: Boolean) =
            apply {
                this.captureWebVitals = enabled
            }

        /** Enables or disables long task capture. Requires JavaScript mode. */
        fun captureLongTasks(enabled: Boolean) =
            apply {
                this.captureLongTasks = enabled
            }

        /** Enables or disables console log capture. Requires JavaScript mode. */
        fun captureConsoleLogs(enabled: Boolean) =
            apply {
                this.captureConsoleLogs = enabled
            }

        /** Enables or disables user interaction capture. Requires JavaScript mode. */
        fun captureUserInteractions(enabled: Boolean) =
            apply {
                this.captureUserInteractions = enabled
            }

        /** Enables or disables error capture. */
        fun captureErrors(enabled: Boolean) =
            apply {
                this.captureErrors = enabled
            }

        /** Builds the [WebViewConfiguration] instance. */
        fun build(): WebViewConfiguration {
            if (instrumentationMode == WebViewInstrumentationMode.NATIVE_ONLY) {
                val jsFeatures =
                    listOfNotNull(
                        if (captureWebVitals) "captureWebVitals" else null,
                        if (captureConsoleLogs) "captureConsoleLogs" else null,
                        if (captureUserInteractions) "captureUserInteractions" else null,
                        if (captureLongTasks) "captureLongTasks" else null,
                        if (captureNavigationEvents) "captureNavigationEvents" else null,
                    )
                if (jsFeatures.isNotEmpty()) {
                    Log.w(
                        LOG_TAG,
                        "WebViewConfiguration: ${jsFeatures.joinToString()} " +
                            "require JavaScript but NATIVE_ONLY mode is set. " +
                            "These features will not work.",
                    )
                }
            }

            return WebViewConfiguration(
                instrumentationMode = instrumentationMode,
                capturePageViews = capturePageViews,
                captureNetworkRequests = captureNetworkRequests,
                captureNavigationEvents = captureNavigationEvents,
                captureWebVitals = captureWebVitals,
                captureLongTasks = captureLongTasks,
                captureConsoleLogs = captureConsoleLogs,
                captureUserInteractions = captureUserInteractions,
                captureErrors = captureErrors,
            )
        }
    }

    /**
     * Factory methods for creating common configurations.
     */
    companion object {
        /** Creates a new [Builder] for configuring WebView instrumentation. */
        @JvmStatic
        fun builder() = Builder()

        /**
         * Creates a native-only configuration that doesn't require JavaScript.
         * Captures basic page load events and errors via WebViewClient callbacks.
         *
         * This is the safest option for security-sensitive applications.
         */
        @JvmStatic
        fun nativeOnly() =
            Builder()
                .instrumentationMode(WebViewInstrumentationMode.NATIVE_ONLY)
                .capturePageViews(true)
                .captureErrors(true)
                .build()

        /**
         * Creates a full instrumentation configuration with JavaScript enabled.
         *
         */
        @JvmStatic
        fun fullWithJavaScript() =
            Builder()
                .instrumentationMode(WebViewInstrumentationMode.FULL_WITH_JAVASCRIPT)
                .capturePageViews(true)
                .captureNetworkRequests(true)
                .captureNavigationEvents(true)
                .captureWebVitals(true)
                .captureLongTasks(true)
                .captureConsoleLogs(true)
                .captureUserInteractions(true)
                .captureErrors(true)
                .build()
    }
}

internal fun WebViewConfiguration?.toFields(): ArrayFields =
    this?.let {
        fieldsOf("_webview_monitoring_configuration" to it.toJson())
    } ?: ArrayFields.EMPTY

internal fun WebViewConfiguration.toJson(): String =
    JSONObject()
        .apply {
            put("instrumentationMode", instrumentationMode.name)
            put("captureConsoleLogs", captureConsoleLogs)
            put("captureErrors", captureErrors)
            put("captureNetworkRequests", captureNetworkRequests)
            put("captureNavigationEvents", captureNavigationEvents)
            put("capturePageViews", capturePageViews)
            put("captureWebVitals", captureWebVitals)
            put("captureLongTasks", captureLongTasks)
            put("captureUserInteractions", captureUserInteractions)
        }.toString()
