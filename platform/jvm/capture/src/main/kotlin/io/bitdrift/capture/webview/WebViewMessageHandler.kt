// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpRequestMetrics
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.toFields
import java.net.URI
import java.util.UUID

/**
 * Handles incoming messages from the WebView JavaScript bridge and routes them
 * to the appropriate logging methods.
 */
internal class WebViewMessageHandler(
    private val logger: LoggerImpl?,
) {
    /**
     * TODO(Fran): Consider switching to kotlinx.serialization
     */
    private val gson by lazy { Gson() }

    private var currentPageSpanId: String? = null
    private val activePageViewSpans = mutableMapOf<String, Span>()

    fun handleMessage(
        message: String,
        capture: WebViewCapture,
    ) {
        val bridgeMessage =
            try {
                gson.fromJson(message, WebViewBridgeMessage::class.java)
            } catch (e: JsonSyntaxException) {
                logger?.log(LogLevel.WARNING, mapOf("_raw" to message, "_error" to e.message.orEmpty())) {
                    "Invalid JSON from WebView bridge"
                }
                return
            }

        // Check protocol version
        if (bridgeMessage.version != 1) {
            logger?.log(LogLevel.WARNING, mapOf("_version" to bridgeMessage.version.toString())) {
                "Unsupported WebView bridge protocol version"
            }
            return
        }

        val type = bridgeMessage.type ?: return
        val timestamp = bridgeMessage.timestamp ?: System.currentTimeMillis()

        when (type) {
            "bridgeReady" -> handleBridgeReady(bridgeMessage, capture)
            "webVital" -> handleWebVital(bridgeMessage, timestamp)
            "networkRequest" -> handleNetworkRequest(bridgeMessage, timestamp)
            "navigation" -> handleNavigation(bridgeMessage, timestamp)
            "pageView" -> handlePageView(bridgeMessage, timestamp)
            "lifecycle" -> handleLifecycle(bridgeMessage, timestamp)
            "error" -> handleError(bridgeMessage, timestamp)
            "longTask" -> handleLongTask(bridgeMessage, timestamp)
            "resourceError" -> handleResourceError(bridgeMessage, timestamp)
            "console" -> handleConsole(bridgeMessage, timestamp)
            "promiseRejection" -> handlePromiseRejection(bridgeMessage, timestamp)
            "userInteraction" -> handleUserInteraction(bridgeMessage, timestamp)
        }
    }

    private fun handleBridgeReady(
        msg: WebViewBridgeMessage,
        capture: WebViewCapture,
    ) {
        capture.onBridgeReady()

        val url = msg.url ?: ""
        val fields = mutableMapOf("_url" to url)
        fields["_source"] = "webview"
        logger?.log(LogLevel.DEBUG, fields) {
            "webview.initialized"
        }
    }

    private fun handleWebVital(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val metric = msg.metric ?: return
        val name = metric.name ?: return
        val value = metric.value ?: return
        val rating = metric.rating ?: "unknown"

        // Extract parentSpanId from the message (set by JS SDK)
        val parentSpanId = msg.parentSpanId ?: currentPageSpanId

        // Determine log level based on rating
        val level =
            when (rating) {
                "good" -> LogLevel.DEBUG
                "needs-improvement" -> LogLevel.INFO
                "poor" -> LogLevel.WARNING
                else -> LogLevel.DEBUG
            }

        // Build common fields for all web vitals
        val commonFields =
            buildMap {
                put("_metric", name)
                put("_value", value.toString())
                put("_rating", rating)
                metric.delta?.let { put("_delta", it.toString()) }
                metric.id?.let { put("_metric_id", it) }
                metric.navigationType?.let { put("_navigation_type", it) }
                parentSpanId?.let { put("_span_parent_id", it) }
                put("_source", "webview")
            }

        // Duration-based metrics are logged as spans (LCP, FCP, TTFB, INP)
        // CLS is a cumulative score, not a duration, so it's logged as a regular log
        when (name) {
            "LCP" -> handleLCPMetric(metric, timestamp, value, level, commonFields, parentSpanId)
            "FCP" -> handleFCPMetric(metric, timestamp, value, level, commonFields, parentSpanId)
            "TTFB" -> handleTTFBMetric(metric, timestamp, value, level, commonFields, parentSpanId)
            "INP" -> handleINPMetric(metric, timestamp, value, level, commonFields, parentSpanId)
            "CLS" -> handleCLSMetric(metric, level, commonFields)
            else -> {
                // Unknown metric type - log as regular log with UX type
                logger?.log(LogType.UX, level, commonFields.toFields()) {
                    "webview.webVital"
                }
            }
        }
    }

    /**
     * Handle Largest Contentful Paint (LCP) metric.
     * LCP measures loading performance - when the largest content element becomes visible.
     * Logged as a span from navigation start to LCP time.
     */
    private fun handleLCPMetric(
        metric: WebVitalMetric,
        timestamp: Long,
        value: Double,
        level: LogLevel,
        commonFields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val fields = commonFields.toMutableMap()
        fields["_metric"] = "LCP"

        // Extract LCP-specific entry data if available
        metric.entries?.firstOrNull()?.let { entry ->
            entry.element?.let { fields["_element"] = it }
            entry.url?.let { fields["_url"] = it }
            entry.size?.let { fields["_size"] = it.toString() }
            entry.renderTime?.let { fields["_render_time"] = it.toString() }
            entry.loadTime?.let { fields["_load_time"] = it.toString() }
        }

        logDurationSpan("webview.webVital", timestamp, value, level, fields, parentSpanId)
    }

    /**
     * Handle First Contentful Paint (FCP) metric.
     * FCP measures when the first content is painted to the screen.
     * Logged as a span from navigation start to FCP time.
     */
    private fun handleFCPMetric(
        metric: WebVitalMetric,
        timestamp: Long,
        value: Double,
        level: LogLevel,
        commonFields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val fields = commonFields.toMutableMap()
        fields["_metric"] = "FCP"

        // Extract FCP-specific entry data if available (PerformancePaintTiming)
        metric.entries?.firstOrNull()?.let { entry ->
            entry.name?.let { fields["_paint_type"] = it }
            entry.startTime?.let { fields["_start_time"] = it.toString() }
            entry.entryType?.let { fields["_entry_type"] = it }
        }

        logDurationSpan("webview.webVital", timestamp, value, level, fields, parentSpanId)
    }

    /**
     * Handle Time to First Byte (TTFB) metric.
     * TTFB measures the time from request start to receiving the first byte of the response.
     * Logged as a span from navigation start to TTFB time.
     */
    private fun handleTTFBMetric(
        metric: WebVitalMetric,
        timestamp: Long,
        value: Double,
        level: LogLevel,
        commonFields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val fields = commonFields.toMutableMap()
        fields["_metric"] = "TTFB"

        // Extract TTFB-specific entry data if available (PerformanceNavigationTiming)
        metric.entries?.firstOrNull()?.let { entry ->
            entry.domainLookupStart?.let { fields["_dns_start"] = it.toString() }
            entry.domainLookupEnd?.let { fields["_dns_end"] = it.toString() }
            entry.connectStart?.let { fields["_connect_start"] = it.toString() }
            entry.connectEnd?.let { fields["_connect_end"] = it.toString() }
            entry.secureConnectionStart?.let { fields["_tls_start"] = it.toString() }
            entry.requestStart?.let { fields["_request_start"] = it.toString() }
            entry.responseStart?.let { fields["_response_start"] = it.toString() }
        }

        logDurationSpan("webview.webVital", timestamp, value, level, fields, parentSpanId)
    }

    /**
     * Handle Interaction to Next Paint (INP) metric.
     * INP measures responsiveness - the time from user interaction to the next frame paint.
     * Logged as a span representing the interaction duration.
     */
    private fun handleINPMetric(
        metric: WebVitalMetric,
        timestamp: Long,
        value: Double,
        level: LogLevel,
        commonFields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val fields = commonFields.toMutableMap()
        fields["_metric"] = "INP"

        // Extract INP-specific entry data if available
        metric.entries?.firstOrNull()?.let { entry ->
            entry.name?.let { fields["_event_type"] = it }
            entry.startTime?.let { fields["_interaction_time"] = it.toString() }
            entry.processingStart?.let { fields["_processing_start"] = it.toString() }
            entry.processingEnd?.let { fields["_processing_end"] = it.toString() }
            entry.duration?.let { fields["_duration"] = it.toString() }
            entry.interactionId?.let { fields["_interaction_id"] = it.toString() }
        }

        logDurationSpan("webview.webVital", timestamp, value, level, fields, parentSpanId)
    }

    /**
     * Handle Cumulative Layout Shift (CLS) metric.
     * CLS measures visual stability - the sum of all unexpected layout shift scores.
     * Unlike other metrics, CLS is a score (0-1+), not a duration, so it's logged as a regular log.
     */
    private fun handleCLSMetric(
        metric: WebVitalMetric,
        level: LogLevel,
        commonFields: Map<String, String>,
    ) {
        val fields = commonFields.toMutableMap()
        fields["_metric"] = "CLS"

        // Extract CLS-specific data from entries
        val entries = metric.entries
        if (!entries.isNullOrEmpty()) {
            // Find the largest shift
            var largestShiftValue = 0.0
            var largestShiftTime = 0.0

            for (entry in entries) {
                val shiftValue = entry.value ?: 0.0
                if (shiftValue > largestShiftValue) {
                    largestShiftValue = shiftValue
                    largestShiftTime = entry.startTime ?: 0.0
                }
            }

            if (largestShiftValue > 0) {
                fields["_largest_shift_value"] = largestShiftValue.toString()
                fields["_largest_shift_time"] = largestShiftTime.toString()
            }

            fields["_shift_count"] = entries.size.toString()
        }

        logger?.log(LogType.UX, level, fields.toFields()) {
            "webview.webVital"
        }
    }

    private fun logDurationSpan(
        spanName: String,
        timestamp: Long,
        durationMs: Double,
        level: LogLevel,
        fields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val startTimeMs = timestamp - durationMs.toLong()

        val result =
            when (fields["_rating"]) {
                "good" -> SpanResult.SUCCESS
                "needs-improvement", "poor" -> SpanResult.FAILURE
                else -> SpanResult.UNKNOWN
            }

        val parentUuid = parentSpanId?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        val span =
            logger?.startSpan(
                name = spanName,
                level = level,
                fields = fields,
                startTimeMs = startTimeMs,
                parentSpanId = parentUuid,
            )
        span?.end(result = result, fields = fields, endTimeMs = timestamp)
    }

    private fun handleNetworkRequest(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val method = msg.method ?: "GET"
        val url = msg.url ?: return
        val statusCode = msg.statusCode
        val durationMs = msg.durationMs ?: 0
        val success = msg.success ?: false
        val errorMessage = msg.error
        val requestType = msg.requestType ?: "unknown"
        val timing = msg.timing

        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host
        val path = uri?.path?.takeIf { it.isNotEmpty() }
        val query = uri?.query

        val extraFields =
            mapOf(
                "_source" to "webview",
                "_request_type" to requestType,
                "_timestamp" to timestamp.toString(),
            )

        val requestInfo =
            HttpRequestInfo(
                method = method,
                host = host,
                path = path?.let { HttpUrlPath(it) },
                query = query,
                extraFields = extraFields,
            )

        val metrics =
            timing?.let { t ->
                HttpRequestMetrics(
                    requestBodyBytesSentCount = 0,
                    responseBodyBytesReceivedCount = t.transferSize ?: 0,
                    requestHeadersBytesCount = 0,
                    responseHeadersBytesCount = 0,
                    dnsResolutionDurationMs = t.dnsMs,
                    tlsDurationMs = t.tlsMs,
                    tcpDurationMs = t.connectMs,
                    responseLatencyMs = t.ttfbMs,
                )
            }

        val result =
            when {
                success -> HttpResponse.HttpResult.SUCCESS
                errorMessage != null -> HttpResponse.HttpResult.FAILURE
                statusCode != null && statusCode >= 400 -> HttpResponse.HttpResult.FAILURE
                else -> HttpResponse.HttpResult.FAILURE
            }

        val responseInfo =
            HttpResponseInfo(
                request = requestInfo,
                response =
                    HttpResponse(
                        result = result,
                        statusCode = statusCode,
                        error = errorMessage?.let(::Exception),
                    ),
                durationMs = durationMs,
                metrics = metrics,
            )

        logger?.log(requestInfo)
        logger?.log(responseInfo)
    }

    private fun handlePageView(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val action = msg.action ?: return
        val spanId = msg.spanId ?: return
        val url = msg.url ?: ""
        val reason = msg.reason ?: ""

        when (action) {
            "start" -> {
                currentPageSpanId = spanId

                val fields =
                    mapOf(
                        "_span_id" to spanId,
                        "_url" to url,
                        "_reason" to reason,
                        "_source" to "webview",
                        "_timestamp" to timestamp.toString(),
                    )

                val span =
                    logger?.startSpan(
                        name = "webview.pageView",
                        level = LogLevel.DEBUG,
                        fields = fields,
                        startTimeMs = timestamp,
                    )
                span?.let { activePageViewSpans[spanId] = it }
            }
            "end" -> {
                val durationMs = msg.durationMs

                val fields =
                    buildMap {
                        put("_span_id", spanId)
                        put("_url", url)
                        put("_reason", reason)
                        put("_source", "webview")
                        put("_timestamp", timestamp.toString())
                        durationMs?.let { put("_duration_ms", it.toString()) }
                    }

                activePageViewSpans.remove(spanId)?.end(
                    result = SpanResult.SUCCESS,
                    fields = fields,
                    endTimeMs = timestamp,
                )

                if (currentPageSpanId == spanId) {
                    currentPageSpanId = null
                }
            }
        }
    }

    private fun handleLifecycle(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val event = msg.event ?: return

        val fields =
            buildMap {
                put("_event", event)
                put("_source", "webview")
                put("_timestamp", timestamp.toString())
                msg.performanceTime?.let { put("_performance_time", it.toString()) }
                msg.visibilityState?.let { put("_visibility_state", it) }
            }

        logger?.log(LogType.UX, LogLevel.DEBUG, fields.toFields()) {
            "webview.lifecycle"
        }
    }

    private fun handleNavigation(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val fromUrl = msg.fromUrl ?: ""
        val toUrl = msg.toUrl ?: ""
        val method = msg.method ?: ""

        val fields =
            mapOf(
                "_fromUrl" to fromUrl,
                "_toUrl" to toUrl,
                "_method" to method,
                "_source" to "webview",
                "_timestamp" to timestamp.toString(),
            )

        logger?.log(LogLevel.DEBUG, fields) {
            "webview.navigation"
        }
    }

    private fun handleError(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val name = msg.name ?: "Error"
        val errorMessage = msg.message ?: "Unknown error"

        val fields =
            buildMap {
                put("_name", name)
                put("_message", errorMessage)
                put("_source", "webview")
                msg.stack?.let { put("_stack", it) }
                msg.filename?.let { put("_filename", it) }
                msg.lineno?.let { put("_lineno", it.toString()) }
                msg.colno?.let { put("_colno", it.toString()) }
                put("_timestamp", timestamp.toString())
            }

        logger?.log(LogLevel.ERROR, fields) {
            "webview.error"
        }
    }

    private fun handleLongTask(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val durationMsDouble = msg.durationMs?.toDouble() ?: return

        val fields =
            buildMap {
                put("_duration_ms", durationMsDouble.toString())
                put("_source", "webview")
                msg.startTime?.let { put("_start_time", it.toString()) }
                put("_timestamp", timestamp.toString())
                msg.attribution?.let { attr ->
                    attr.name?.let { put("_attribution_name", it) }
                    attr.containerType?.let { put("_container_type", it) }
                    attr.containerSrc?.let { put("_container_src", it) }
                    attr.containerId?.let { put("_container_id", it) }
                    attr.containerName?.let { put("_container_name", it) }
                }
            }

        val level =
            when {
                durationMsDouble >= 200 -> LogLevel.WARNING
                durationMsDouble >= 100 -> LogLevel.INFO
                else -> LogLevel.DEBUG
            }

        logger?.log(LogType.UX, level, fields.toFields()) {
            "webview.longTask"
        }
    }

    private fun handleResourceError(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val fields =
            mapOf(
                "_resource_type" to (msg.resourceType ?: "unknown"),
                "_url" to (msg.url ?: ""),
                "_tag_name" to (msg.tagName ?: ""),
                "_source" to "webview",
                "_timestamp" to timestamp.toString(),
            )

        logger?.log(LogLevel.WARNING, fields) {
            "webview.resourceError"
        }
    }

    private fun handleConsole(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val level = msg.level ?: "log"
        val consoleMessage = msg.message ?: ""

        val fields =
            buildMap {
                put("_level", level)
                put("_message", consoleMessage)
                put("_source", "webview")
                put("_timestamp", timestamp.toString())
                msg.args?.takeIf { it.isNotEmpty() }?.let { args ->
                    put("_args", args.take(5).joinToString(", "))
                }
            }

        val logLevel =
            when (level) {
                "error" -> LogLevel.ERROR
                "warn" -> LogLevel.WARNING
                "info" -> LogLevel.INFO
                else -> LogLevel.DEBUG
            }

        logger?.log(logLevel, fields) {
            "webview.console"
        }
    }

    private fun handlePromiseRejection(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val fields =
            buildMap {
                put("_reason", msg.reason ?: "Unknown rejection")
                put("_source", "webview")
                msg.stack?.let { put("_stack", it) }
                put("_timestamp", timestamp.toString())
            }

        logger?.log(LogLevel.ERROR, fields) {
            "webview.promiseRejection"
        }
    }

    private fun handleUserInteraction(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val interactionType = msg.interactionType ?: return

        val fields =
            buildMap {
                put("_interaction_type", interactionType)
                put("_tag_name", msg.tagName ?: "")
                put("_is_clickable", (msg.isClickable ?: false).toString())
                put("_source", "webview")
                msg.elementId?.let { put("_element_id", it) }
                msg.className?.let { put("_class_name", it) }
                msg.textContent?.let { put("_text_content", it) }
                msg.clickCount?.let { put("_click_count", it.toString()) }
                msg.timeWindowMs?.let { put("_time_window_ms", it.toString()) }
                put("_timestamp", timestamp.toString())
            }

        val level = if (interactionType == "rageClick") LogLevel.WARNING else LogLevel.DEBUG
        logger?.log(LogType.UX, level, fields.toFields()) {
            "webview.userInteraction"
        }
    }
}
