// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
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
    private val gson = Gson()

    /** Current page view span ID for nesting child events */
    private var currentPageSpanId: String? = null

    /** Active page view spans, keyed by span ID */
    private val activePageViewSpans = mutableMapOf<String, io.bitdrift.capture.events.span.Span>()

    fun handleMessage(
        message: String,
        capture: WebViewCapture,
    ) {
        val json =
            try {
                gson.fromJson(message, JsonObject::class.java)
            } catch (e: JsonSyntaxException) {
                logger?.log(LogLevel.WARNING, mapOf("_raw" to message.take(100))) {
                    "Invalid JSON from WebView bridge"
                }
                return
            }

        // Check protocol version
        val version = json.get("v")?.asInt ?: 0
        if (version != 1) {
            logger?.log(LogLevel.WARNING, mapOf("_version" to version.toString())) {
                "Unsupported WebView bridge protocol version"
            }
            return
        }

        val type = json.get("type")?.asString ?: return
        val timestamp = json.get("timestamp")?.asLong ?: System.currentTimeMillis()

        when (type) {
            "bridgeReady" -> handleBridgeReady(json, capture)
            "webVital" -> handleWebVital(json, timestamp)
            "networkRequest" -> handleNetworkRequest(json, timestamp)
            "navigation" -> handleNavigation(json, timestamp)
            "pageView" -> handlePageView(json, timestamp)
            "lifecycle" -> handleLifecycle(json, timestamp)
            "error" -> handleError(json, timestamp)
            "longTask" -> handleLongTask(json, timestamp)
            "resourceError" -> handleResourceError(json, timestamp)
            "console" -> handleConsole(json, timestamp)
            "promiseRejection" -> handlePromiseRejection(json, timestamp)
            "userInteraction" -> handleUserInteraction(json, timestamp)
        }
    }

    private fun handleBridgeReady(
        json: JsonObject,
        capture: WebViewCapture,
    ) {
        capture.onBridgeReady()

        val url = json.get("url")?.asString ?: ""
        logger?.log(LogLevel.DEBUG, mapOf("_url" to url)) {
            "WebView bridge ready"
        }
    }

    private fun handleWebVital(
        json: JsonObject,
        timestamp: Long,
    ) {
        val metric = json.getAsJsonObject("metric") ?: return
        val name = metric.get("name")?.asString ?: return
        val value = metric.get("value")?.asDouble ?: return
        val rating = metric.get("rating")?.asString ?: "unknown"
        val delta = metric.get("delta")?.asDouble
        val id = metric.get("id")?.asString
        val navigationType = metric.get("navigationType")?.asString

        // Extract parentSpanId from the message (set by JS SDK)
        val parentSpanId = json.get("parentSpanId")?.asString ?: currentPageSpanId

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
                delta?.let { put("_delta", it.toString()) }
                id?.let { put("_metric_id", it) }
                navigationType?.let { put("_navigation_type", it) }
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
                    "webview.webVital.$name"
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
        metric: JsonObject,
        timestamp: Long,
        value: Double,
        level: LogLevel,
        commonFields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val fields = commonFields.toMutableMap()

        // Extract LCP-specific entry data if available
        metric.getAsJsonArray("entries")?.firstOrNull()?.asJsonObject?.let { entry ->
            entry.get("element")?.asString?.let { fields["_element"] = it }
            entry.get("url")?.asString?.let { fields["_url"] = it }
            entry.get("size")?.asLong?.let { fields["_size"] = it.toString() }
            entry.get("renderTime")?.asDouble?.let { fields["_render_time"] = it.toString() }
            entry.get("loadTime")?.asDouble?.let { fields["_load_time"] = it.toString() }
        }

        logDurationSpan("webview.LCP", timestamp, value, level, fields, parentSpanId)
    }

    /**
     * Handle First Contentful Paint (FCP) metric.
     * FCP measures when the first content is painted to the screen.
     * Logged as a span from navigation start to FCP time.
     */
    private fun handleFCPMetric(
        metric: JsonObject,
        timestamp: Long,
        value: Double,
        level: LogLevel,
        commonFields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val fields = commonFields.toMutableMap()

        // Extract FCP-specific entry data if available (PerformancePaintTiming)
        metric.getAsJsonArray("entries")?.firstOrNull()?.asJsonObject?.let { entry ->
            entry.get("name")?.asString?.let { fields["_paint_type"] = it }
            entry.get("startTime")?.asDouble?.let { fields["_start_time"] = it.toString() }
            entry.get("entryType")?.asString?.let { fields["_entry_type"] = it }
        }

        logDurationSpan("webview.FCP", timestamp, value, level, fields, parentSpanId)
    }

    /**
     * Handle Time to First Byte (TTFB) metric.
     * TTFB measures the time from request start to receiving the first byte of the response.
     * Logged as a span from navigation start to TTFB time.
     */
    private fun handleTTFBMetric(
        metric: JsonObject,
        timestamp: Long,
        value: Double,
        level: LogLevel,
        commonFields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val fields = commonFields.toMutableMap()

        // Extract TTFB-specific entry data if available (PerformanceNavigationTiming)
        metric.getAsJsonArray("entries")?.firstOrNull()?.asJsonObject?.let { entry ->
            entry.get("domainLookupStart")?.asDouble?.let { fields["_dns_start"] = it.toString() }
            entry.get("domainLookupEnd")?.asDouble?.let { fields["_dns_end"] = it.toString() }
            entry.get("connectStart")?.asDouble?.let { fields["_connect_start"] = it.toString() }
            entry.get("connectEnd")?.asDouble?.let { fields["_connect_end"] = it.toString() }
            entry.get("secureConnectionStart")?.asDouble?.let { fields["_tls_start"] = it.toString() }
            entry.get("requestStart")?.asDouble?.let { fields["_request_start"] = it.toString() }
            entry.get("responseStart")?.asDouble?.let { fields["_response_start"] = it.toString() }
        }

        logDurationSpan("webview.TTFB", timestamp, value, level, fields, parentSpanId)
    }

    /**
     * Handle Interaction to Next Paint (INP) metric.
     * INP measures responsiveness - the time from user interaction to the next frame paint.
     * Logged as a span representing the interaction duration.
     */
    private fun handleINPMetric(
        metric: JsonObject,
        timestamp: Long,
        value: Double,
        level: LogLevel,
        commonFields: Map<String, String>,
        parentSpanId: String?,
    ) {
        val fields = commonFields.toMutableMap()

        // Extract INP-specific entry data if available
        metric.getAsJsonArray("entries")?.firstOrNull()?.asJsonObject?.let { entry ->
            entry.get("name")?.asString?.let { fields["_event_type"] = it }
            entry.get("startTime")?.asDouble?.let { fields["_interaction_time"] = it.toString() }
            entry.get("processingStart")?.asDouble?.let { fields["_processing_start"] = it.toString() }
            entry.get("processingEnd")?.asDouble?.let { fields["_processing_end"] = it.toString() }
            entry.get("duration")?.asDouble?.let { fields["_duration"] = it.toString() }
            entry.get("interactionId")?.asLong?.let { fields["_interaction_id"] = it.toString() }
        }

        logDurationSpan("webview.INP", timestamp, value, level, fields, parentSpanId)
    }

    /**
     * Handle Cumulative Layout Shift (CLS) metric.
     * CLS measures visual stability - the sum of all unexpected layout shift scores.
     * Unlike other metrics, CLS is a score (0-1+), not a duration, so it's logged as a regular log.
     */
    private fun handleCLSMetric(
        metric: JsonObject,
        level: LogLevel,
        commonFields: Map<String, String>,
    ) {
        val fields = commonFields.toMutableMap()

        // Extract CLS-specific data from entries
        val entries = metric.getAsJsonArray("entries")
        if (entries != null && entries.size() > 0) {
            // Find the largest shift
            var largestShiftValue = 0.0
            var largestShiftTime = 0.0

            for (entry in entries) {
                val entryObj = entry.asJsonObject
                val shiftValue = entryObj.get("value")?.asDouble ?: 0.0
                if (shiftValue > largestShiftValue) {
                    largestShiftValue = shiftValue
                    largestShiftTime = entryObj.get("startTime")?.asDouble ?: 0.0
                }
            }

            if (largestShiftValue > 0) {
                fields["_largest_shift_value"] = largestShiftValue.toString()
                fields["_largest_shift_time"] = largestShiftTime.toString()
            }

            fields["_shift_count"] = entries.size().toString()
        }

        logger?.log(LogType.UX, level, fields.toFields()) {
            "webview.CLS"
        }
    }

    /**
     * Log a duration-based web vital as a span with custom start/end times.
     * The start time is calculated as (timestamp - value) where value is the duration in ms,
     * and end time is the timestamp when the metric was reported.
     */
    private fun logDurationSpan(
        spanName: String,
        timestamp: Long,
        durationMs: Double,
        level: LogLevel,
        fields: Map<String, String>,
        parentSpanId: String?,
    ) {
        // Calculate start time: the metric value represents duration from navigation start
        // timestamp is when the metric was captured (effectively the end time)
        val startTimeMs = timestamp - durationMs.toLong()
        val endTimeMs = timestamp

        // Determine span result based on rating
        val result =
            when (fields["_rating"]) {
                "good" -> SpanResult.SUCCESS
                "needs-improvement", "poor" -> SpanResult.FAILURE
                else -> SpanResult.UNKNOWN
            }

        // Convert parentSpanId string to UUID
        val parentUuid =
            parentSpanId?.let {
                try {
                    UUID.fromString(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        // Start span with custom start time and parent span ID
        val span =
            logger?.startSpan(
                name = spanName,
                level = level,
                fields = fields,
                startTimeMs = startTimeMs,
                parentSpanId = parentUuid,
            )

        // End span with custom end time
        span?.end(
            result = result,
            fields = fields,
            endTimeMs = endTimeMs,
        )
    }

    private fun handleNetworkRequest(
        json: JsonObject,
        timestamp: Long,
    ) {
        val method = json.get("method")?.asString ?: "GET"
        val url = json.get("url")?.asString ?: return
        val statusCode = json.get("statusCode")?.asInt
        val durationMs = json.get("durationMs")?.asLong ?: 0
        val success = json.get("success")?.asBoolean ?: false
        val errorMessage = json.get("error")?.asString
        val requestType = json.get("requestType")?.asString ?: "unknown"
        val timing = json.getAsJsonObject("timing")

        // Parse URL components
        val uri =
            try {
                URI(url)
            } catch (e: Exception) {
                null
            }

        val host = uri?.host
        val path = uri?.path?.takeIf { it.isNotEmpty() }
        val query = uri?.query

        // Build extra fields for WebView-specific data
        val extraFields =
            buildMap {
                put("_source", "webview")
                put("_request_type", requestType)
                put("_timestamp", timestamp.toString())
            }

        // Create HttpRequestInfo
        val requestInfo =
            HttpRequestInfo(
                method = method,
                host = host,
                path = path?.let { HttpUrlPath(it) },
                query = query,
                extraFields = extraFields,
            )

        // Build metrics from timing data if available
        val metrics =
            timing?.let { t ->
                HttpRequestMetrics(
                    requestBodyBytesSentCount = 0,
                    responseBodyBytesReceivedCount = t.get("transferSize")?.asLong ?: 0,
                    requestHeadersBytesCount = 0,
                    responseHeadersBytesCount = 0,
                    dnsResolutionDurationMs = t.get("dnsMs")?.asLong,
                    tlsDurationMs = t.get("tlsMs")?.asLong,
                    tcpDurationMs = t.get("connectMs")?.asLong,
                    responseLatencyMs = t.get("ttfbMs")?.asLong,
                )
            }

        // Determine result based on success/error
        val result =
            when {
                success -> HttpResponse.HttpResult.SUCCESS
                errorMessage != null -> HttpResponse.HttpResult.FAILURE
                statusCode != null && statusCode >= 400 -> HttpResponse.HttpResult.FAILURE
                else -> HttpResponse.HttpResult.FAILURE
            }

        // Build error if present
        val error = errorMessage?.let { Exception(it) }

        // Create HttpResponseInfo
        val responseInfo =
            HttpResponseInfo(
                request = requestInfo,
                response =
                    HttpResponse(
                        result = result,
                        statusCode = statusCode,
                        error = error,
                    ),
                durationMs = durationMs,
                metrics = metrics,
            )

        // Log the request and response as a span pair
        logger?.log(requestInfo)
        logger?.log(responseInfo)
    }

    /**
     * Handle page view span start/end messages.
     * Page view spans group all events within a single page session.
     */
    private fun handlePageView(
        json: JsonObject,
        timestamp: Long,
    ) {
        val action = json.get("action")?.asString ?: return
        val spanId = json.get("spanId")?.asString ?: return
        val url = json.get("url")?.asString ?: ""
        val reason = json.get("reason")?.asString ?: ""

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

                // Start the page view span (include URL in name for visibility)
                val span =
                    logger?.startSpan(
                        name = "webview.pageView: $url",
                        level = LogLevel.DEBUG,
                        fields = fields,
                        startTimeMs = timestamp,
                    )

                // Store the span for later ending
                if (span != null) {
                    activePageViewSpans[spanId] = span
                }
            }
            "end" -> {
                val durationMs = json.get("durationMs")?.asDouble

                val fields =
                    buildMap {
                        put("_span_id", spanId)
                        put("_url", url)
                        put("_reason", reason)
                        put("_source", "webview")
                        put("_timestamp", timestamp.toString())
                        durationMs?.let { put("_duration_ms", it.toString()) }
                    }

                // End the page view span
                activePageViewSpans.remove(spanId)?.end(
                    result = SpanResult.SUCCESS,
                    fields = fields,
                    endTimeMs = timestamp,
                )

                // Clear current page span ID if it matches
                if (currentPageSpanId == spanId) {
                    currentPageSpanId = null
                }
            }
        }
    }

    /**
     * Handle lifecycle events (DOMContentLoaded, load, visibilitychange).
     * These are markers within the page view span.
     */
    private fun handleLifecycle(
        json: JsonObject,
        timestamp: Long,
    ) {
        val event = json.get("event")?.asString ?: return
        val performanceTime = json.get("performanceTime")?.asDouble
        val visibilityState = json.get("visibilityState")?.asString

        val fields =
            buildMap {
                put("_event", event)
                put("_source", "webview")
                put("_timestamp", timestamp.toString())
                performanceTime?.let { put("_performance_time", it.toString()) }
                visibilityState?.let { put("_visibility_state", it) }
            }

        // Log lifecycle event as UX type
        logger?.log(LogType.UX, LogLevel.DEBUG, fields.toFields()) {
            "webview.lifecycle.$event"
        }
    }

    private fun handleNavigation(
        json: JsonObject,
        timestamp: Long,
    ) {
        val fromUrl = json.get("fromUrl")?.asString ?: ""
        val toUrl = json.get("toUrl")?.asString ?: ""
        val method = json.get("method")?.asString ?: ""

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
        json: JsonObject,
        timestamp: Long,
    ) {
        val name = json.get("name")?.asString ?: "Error"
        val errorMessage = json.get("message")?.asString ?: "Unknown error"
        val stack = json.get("stack")?.asString
        val filename = json.get("filename")?.asString
        val lineno = json.get("lineno")?.asInt
        val colno = json.get("colno")?.asInt

        val fields =
            buildMap {
                put("_name", name)
                put("_message", errorMessage)
                put("_source", "webview")
                stack?.let { put("_stack", it.take(1000)) } // Limit stack trace size
                filename?.let { put("_filename", it) }
                lineno?.let { put("_lineno", it.toString()) }
                colno?.let { put("_colno", it.toString()) }
                put("_timestamp", timestamp.toString())
            }

        logger?.log(LogLevel.ERROR, fields) {
            "webview.error"
        }
    }

    /**
     * Handle long task events (main thread blocked > 50ms).
     */
    private fun handleLongTask(
        json: JsonObject,
        timestamp: Long,
    ) {
        val durationMs = json.get("durationMs")?.asDouble ?: return
        val startTime = json.get("startTime")?.asDouble

        val fields =
            buildMap {
                put("_duration_ms", durationMs.toString())
                put("_source", "webview")
                startTime?.let { put("_start_time", it.toString()) }
                put("_timestamp", timestamp.toString())

                // Extract attribution data
                json.getAsJsonObject("attribution")?.let { attr ->
                    attr.get("name")?.asString?.let { put("_attribution_name", it) }
                    attr.get("containerType")?.asString?.let { put("_container_type", it) }
                    attr.get("containerSrc")?.asString?.let { put("_container_src", it) }
                    attr.get("containerId")?.asString?.let { put("_container_id", it) }
                    attr.get("containerName")?.asString?.let { put("_container_name", it) }
                }
            }

        // Determine log level based on duration
        val level =
            when {
                durationMs >= 200 -> LogLevel.WARNING
                durationMs >= 100 -> LogLevel.INFO
                else -> LogLevel.DEBUG
            }

        logger?.log(LogType.UX, level, fields.toFields()) {
            "webview.longTask"
        }
    }

    /**
     * Handle resource loading failures (images, scripts, stylesheets, etc.).
     */
    private fun handleResourceError(
        json: JsonObject,
        timestamp: Long,
    ) {
        val resourceType = json.get("resourceType")?.asString ?: "unknown"
        val url = json.get("url")?.asString ?: ""
        val tagName = json.get("tagName")?.asString ?: ""

        val fields =
            buildMap {
                put("_resource_type", resourceType)
                put("_url", url)
                put("_tag_name", tagName)
                put("_source", "webview")
                put("_timestamp", timestamp.toString())
            }

        logger?.log(LogLevel.WARNING, fields) {
            "webview.resourceError"
        }
    }

    /**
     * Handle console messages (log, warn, error, info, debug).
     */
    private fun handleConsole(
        json: JsonObject,
        timestamp: Long,
    ) {
        val level = json.get("level")?.asString ?: "log"
        val consoleMessage = json.get("message")?.asString ?: ""

        val fields =
            buildMap {
                put("_level", level)
                put("_message", consoleMessage)
                put("_source", "webview")
                put("_timestamp", timestamp.toString())

                // Extract additional args if present
                json.getAsJsonArray("args")?.let { args ->
                    if (args.size() > 0) {
                        val argsStr = args.mapNotNull { it.asString }.take(5).joinToString(", ")
                        put("_args", argsStr)
                    }
                }
            }

        // Map console level to LogLevel
        val logLevel =
            when (level) {
                "error" -> LogLevel.ERROR
                "warn" -> LogLevel.WARNING
                "info" -> LogLevel.INFO
                else -> LogLevel.DEBUG
            }

        logger?.log(logLevel, fields) {
            "webview.console.$level"
        }
    }

    /**
     * Handle unhandled promise rejections.
     */
    private fun handlePromiseRejection(
        json: JsonObject,
        timestamp: Long,
    ) {
        val reason = json.get("reason")?.asString ?: "Unknown rejection"
        val stack = json.get("stack")?.asString

        val fields =
            buildMap {
                put("_reason", reason)
                put("_source", "webview")
                stack?.let { put("_stack", it.take(1000)) }
                put("_timestamp", timestamp.toString())
            }

        logger?.log(LogLevel.ERROR, fields) {
            "webview.promiseRejection"
        }
    }

    /**
     * Handle user interaction events (clicks and rage clicks).
     */
    private fun handleUserInteraction(
        json: JsonObject,
        timestamp: Long,
    ) {
        val interactionType = json.get("interactionType")?.asString ?: return
        val tagName = json.get("tagName")?.asString ?: ""
        val elementId = json.get("elementId")?.asString
        val className = json.get("className")?.asString
        val textContent = json.get("textContent")?.asString
        val isClickable = json.get("isClickable")?.asBoolean ?: false
        val clickCount = json.get("clickCount")?.asInt
        val timeWindowMs = json.get("timeWindowMs")?.asInt

        val fields =
            buildMap {
                put("_interaction_type", interactionType)
                put("_tag_name", tagName)
                put("_is_clickable", isClickable.toString())
                put("_source", "webview")
                elementId?.let { put("_element_id", it) }
                className?.let { put("_class_name", it.take(100)) }
                textContent?.let { put("_text_content", it.take(50)) }
                clickCount?.let { put("_click_count", it.toString()) }
                timeWindowMs?.let { put("_time_window_ms", it.toString()) }
                put("_timestamp", timestamp.toString())
            }

        // Rage clicks are more important
        val level = if (interactionType == "rageClick") LogLevel.WARNING else LogLevel.DEBUG

        logger?.log(LogType.UX, level, fields.toFields()) {
            "webview.userInteraction.$interactionType"
        }
    }
}
