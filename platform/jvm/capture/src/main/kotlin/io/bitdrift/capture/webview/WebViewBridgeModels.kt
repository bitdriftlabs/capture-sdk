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
 * Most message-specific data is now in the pre-formatted `fields` map.
 */
internal data class WebViewBridgeMessage(
    @SerializedName("v") val version: Int = 0,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    // Pre-formatted log fields (already in _snake_case format)
    @SerializedName("fields") val fields: Map<String, Any>? = null,
    // Fields used for business logic / routing
    @SerializedName("level") val level: String? = null, // console, customLog
    @SerializedName("message") val message: String? = null, // customLog
    @SerializedName("event") val event: String? = null, // internalAutoInstrumentation
    @SerializedName("metric") val metric: WebVitalMetric? = null, // webVital
    @SerializedName("parentSpanId") val parentSpanId: String? = null, // webVital
    @SerializedName("action") val action: String? = null, // pageView
    @SerializedName("spanId") val spanId: String? = null, // pageView
    @SerializedName("durationMs") val durationMs: Long? = null, // longTask, networkRequest
    @SerializedName("interactionType") val interactionType: String? = null, // userInteraction
    // networkRequest - retained due to complex business logic
    @SerializedName("method") val method: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("statusCode") val statusCode: Int? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("requestType") val requestType: String? = null,
    @SerializedName("timing") val timing: NetworkTiming? = null,
)

/**
 * Web Vital metric data from the web-vitals library.
 * Used to determine log level and span behavior.
 */
internal data class WebVitalMetric(
    @SerializedName("name") val name: String? = null,
    @SerializedName("value") val value: Double? = null,
    @SerializedName("rating") val rating: String? = null,
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
