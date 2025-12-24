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
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    // bridgeReady
    @SerializedName("url") val url: String? = null,
    // webVital
    @SerializedName("metric") val metric: WebVitalMetric? = null,
    @SerializedName("parentSpanId") val parentSpanId: String? = null,
    // networkRequest
    @SerializedName("method") val method: String? = null,
    @SerializedName("statusCode") val statusCode: Int? = null,
    @SerializedName("durationMs") val durationMs: Long? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("requestType") val requestType: String? = null,
    @SerializedName("timing") val timing: NetworkTiming? = null,
    // navigation
    @SerializedName("fromUrl") val fromUrl: String? = null,
    @SerializedName("toUrl") val toUrl: String? = null,
    // pageView
    @SerializedName("action") val action: String? = null,
    @SerializedName("spanId") val spanId: String? = null,
    @SerializedName("reason") val reason: String? = null,
    // lifecycle
    @SerializedName("event") val event: String? = null,
    @SerializedName("performanceTime") val performanceTime: Double? = null,
    @SerializedName("visibilityState") val visibilityState: String? = null,
    // error
    @SerializedName("name") val name: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("stack") val stack: String? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("lineno") val lineno: Int? = null,
    @SerializedName("colno") val colno: Int? = null,
    // longTask
    @SerializedName("startTime") val startTime: Double? = null,
    @SerializedName("attribution") val attribution: LongTaskAttribution? = null,
    // resourceError
    @SerializedName("resourceType") val resourceType: String? = null,
    @SerializedName("tagName") val tagName: String? = null,
    // console
    @SerializedName("level") val level: String? = null,
    @SerializedName("args") val args: List<String>? = null,
    // userInteraction
    @SerializedName("interactionType") val interactionType: String? = null,
    @SerializedName("elementId") val elementId: String? = null,
    @SerializedName("className") val className: String? = null,
    @SerializedName("textContent") val textContent: String? = null,
    @SerializedName("isClickable") val isClickable: Boolean? = null,
    @SerializedName("clickCount") val clickCount: Int? = null,
    @SerializedName("timeWindowMs") val timeWindowMs: Int? = null,
)

/**
 * Web Vital metric data from the web-vitals library.
 */
internal data class WebVitalMetric(
    @SerializedName("name") val name: String? = null,
    @SerializedName("value") val value: Double? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("delta") val delta: Double? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("navigationType") val navigationType: String? = null,
    @SerializedName("entries") val entries: List<WebVitalEntry>? = null,
)

/**
 * Performance entry associated with a web vital metric.
 * Contains different fields depending on the metric type.
 */
internal data class WebVitalEntry(
    // Common fields
    @SerializedName("startTime") val startTime: Double? = null,
    @SerializedName("entryType") val entryType: String? = null,
    // LCP-specific
    @SerializedName("element") val element: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("renderTime") val renderTime: Double? = null,
    @SerializedName("loadTime") val loadTime: Double? = null,
    // FCP-specific
    @SerializedName("name") val name: String? = null,
    // TTFB-specific (PerformanceNavigationTiming)
    @SerializedName("domainLookupStart") val domainLookupStart: Double? = null,
    @SerializedName("domainLookupEnd") val domainLookupEnd: Double? = null,
    @SerializedName("connectStart") val connectStart: Double? = null,
    @SerializedName("connectEnd") val connectEnd: Double? = null,
    @SerializedName("secureConnectionStart") val secureConnectionStart: Double? = null,
    @SerializedName("requestStart") val requestStart: Double? = null,
    @SerializedName("responseStart") val responseStart: Double? = null,
    // INP-specific
    @SerializedName("processingStart") val processingStart: Double? = null,
    @SerializedName("processingEnd") val processingEnd: Double? = null,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("interactionId") val interactionId: Long? = null,
    // CLS-specific
    @SerializedName("value") val value: Double? = null,
)

/**
 * Network timing data from the Performance API.
 */
internal data class NetworkTiming(
    @SerializedName("transferSize") val transferSize: Long? = null,
    @SerializedName("dnsMs") val dnsMs: Long? = null,
    @SerializedName("tlsMs") val tlsMs: Long? = null,
    @SerializedName("connectMs") val connectMs: Long? = null,
    @SerializedName("ttfbMs") val ttfbMs: Long? = null,
)

/**
 * Long task attribution data.
 */
internal data class LongTaskAttribution(
    @SerializedName("name") val name: String? = null,
    @SerializedName("containerType") val containerType: String? = null,
    @SerializedName("containerSrc") val containerSrc: String? = null,
    @SerializedName("containerId") val containerId: String? = null,
    @SerializedName("containerName") val containerName: String? = null,
)
