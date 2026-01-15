// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import com.google.gson.annotations.SerializedName

/**
 * Base class for all WebView bridge messages.
 * All messages have a version, type, and timestamp.
 */
internal data class WebViewBridgeMessage(
    @SerializedName("v") val version: Int = 0,
    val tag: String? = null,
    val type: String? = null,
    val timestamp: Long? = null,
    // bridgeReady
    val url: String? = null,
    // webVital
    val metric: WebVitalMetric? = null,
    val parentSpanId: String? = null,
    // networkRequest
    val method: String? = null,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val success: Boolean? = null,
    val error: String? = null,
    val requestType: String? = null,
    val timing: NetworkTiming? = null,
    // navigation
    val fromUrl: String? = null,
    val toUrl: String? = null,
    // pageView
    val action: String? = null,
    val spanId: String? = null,
    val reason: String? = null,
    // lifecycle
    val event: String? = null,
    val performanceTime: Double? = null,
    val visibilityState: String? = null,
    // error
    val name: String? = null,
    val message: String? = null,
    val stack: String? = null,
    val filename: String? = null,
    val lineno: Int? = null,
    val colno: Int? = null,
    // longTask
    val startTime: Double? = null,
    val attribution: LongTaskAttribution? = null,
    // resourceError
    val resourceType: String? = null,
    val tagName: String? = null,
    // console
    val level: String? = null,
    val args: List<String>? = null,
    // userInteraction
    val interactionType: String? = null,
    val elementId: String? = null,
    val className: String? = null,
    val textContent: String? = null,
    val isClickable: Boolean? = null,
    val clickCount: Int? = null,
    val timeWindowMs: Int? = null,
)

/**
 * Web Vital metric data from the web-vitals library.
 */
internal data class WebVitalMetric(
    val name: String? = null,
    val value: Double? = null,
    val rating: String? = null,
    val delta: Double? = null,
    val id: String? = null,
    val navigationType: String? = null,
    val entries: List<WebVitalEntry>? = null,
)

/**
 * Performance entry associated with a web vital metric.
 * Contains different fields depending on the metric type.
 */
internal data class WebVitalEntry(
    // Common fields
    val startTime: Double? = null,
    val entryType: String? = null,
    // LCP-specific
    val element: String? = null,
    val url: String? = null,
    val size: Long? = null,
    val renderTime: Double? = null,
    val loadTime: Double? = null,
    // FCP-specific
    val name: String? = null,
    // TTFB-specific (PerformanceNavigationTiming)
    val domainLookupStart: Double? = null,
    val domainLookupEnd: Double? = null,
    val connectStart: Double? = null,
    val connectEnd: Double? = null,
    val secureConnectionStart: Double? = null,
    val requestStart: Double? = null,
    val responseStart: Double? = null,
    // INP-specific
    val processingStart: Double? = null,
    val processingEnd: Double? = null,
    val duration: Double? = null,
    val interactionId: Long? = null,
    // CLS-specific
    val value: Double? = null,
)

/**
 * Network timing data from the Performance API.
 */
internal data class NetworkTiming(
    val transferSize: Long? = null,
    val dnsMs: Long? = null,
    val tlsMs: Long? = null,
    val connectMs: Long? = null,
    val ttfbMs: Long? = null,
)

/**
 * Long task attribution data.
 */
internal data class LongTaskAttribution(
    val name: String? = null,
    val containerType: String? = null,
    val containerSrc: String? = null,
    val containerId: String? = null,
    val containerName: String? = null,
)
