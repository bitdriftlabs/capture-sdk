// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.webview

import android.webkit.JavascriptInterface
import com.google.gson.Gson
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpRequestMetrics
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.providers.fieldsOfOptional
import io.bitdrift.capture.providers.toFields
import java.net.URI
import java.util.UUID

/**
 * Handles incoming messages from the WebView JavaScript bridge and routes them
 * to the appropriate logging methods.
 */
internal class WebViewBridgeMessageHandler(
    private val logger: IInternalLogger,
) {
    /**
     * TODO(Fran): BIT-5074. Consider switching to kotlinx.serialization
     */
    private val gson by lazy { Gson() }

    private var currentPageSpanId: String? = null
    private val activePageViewSpans = mutableMapOf<String, Span>()

    /**
     * Extract and convert the pre-formatted fields from the message.
     * Fields are already in the correct format (_snake_case) from the JS layer.
     */
    private fun extractFields(msg: WebViewBridgeMessage): Map<String, String> {
        return msg.fields?.mapValues { it.value.toString() } ?: emptyMap()
    }

    /**
     * JavaScript interface that receives messages from the injected bridge script.
     */
    @JavascriptInterface
    fun log(message: String) {
        val bridgeMessage =
            runCatching {
                gson.fromJson(message, WebViewBridgeMessage::class.java)
            }.getOrElse { throwable ->
                logger.handleInternalError("Failed to extract WebView bridge message. $message", throwable)
                return
            }

        if (bridgeMessage == null) {
            logger.handleInternalError("WebView bridge message is null after parsing", null)
            return
        }

        if (bridgeMessage.version != 1) {
            logger.log(LogLevel.WARNING, fieldsOf("_version" to bridgeMessage.version.toString())) {
                "Unsupported WebView bridge protocol version"
            }
            return
        }

        runCatching {
            val type = bridgeMessage.type ?: return
            val timestamp = bridgeMessage.timestamp ?: System.currentTimeMillis()

            when (type) {
                "customLog" -> handleCustomLog(bridgeMessage, timestamp)
                "bridgeReady" -> handleBridgeReady(bridgeMessage)
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
                "internalAutoInstrumentation" -> handleInternalAutoInstrumentation(bridgeMessage, timestamp)
                else -> {
                    logger.handleInternalError("Unknown WebView bridge message. $message")
                }
            }
        }.getOrElse { throwable ->
            logger.handleInternalError("Failed to handle WebView bridge message. $message", throwable)
        }
    }

    private fun handleInternalAutoInstrumentation(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val fields = extractFields(msg)

        logger.logInternal(LogType.INTERNALSDK, LogLevel.DEBUG, fields.toFields()) {
            "[WebView] instrumented ${msg.event}"
        }
    }

    private fun handleCustomLog(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val levelStr = msg.level ?: "debug"
        val message = msg.message ?: ""

        val fields =
            buildMap {
                put("_source", "webview")
                put("_timestamp", timestamp.toString())
                msg.fields?.forEach { (key, value) ->
                    put(key, value.toString())
                }
            }

        val level =
            when (levelStr.lowercase()) {
                "info" -> LogLevel.INFO
                "warn" -> LogLevel.WARNING
                "error" -> LogLevel.ERROR
                "trace" -> LogLevel.TRACE
                else -> LogLevel.DEBUG
            }

        logger.log(level, fields) {
            message
        }
    }

    private fun handleBridgeReady(msg: WebViewBridgeMessage) {
        val fields = extractFields(msg)
        
        logger.log(LogLevel.DEBUG, fields.toFields()) {
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
        
        // Extract pre-formatted fields from JS
        val fields = extractFields(msg)

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

        // Duration-based metrics are logged as spans (LCP, FCP, TTFB, INP)
        // CLS is a cumulative score, not a duration, so it's logged as a regular log
        when (name) {
            "LCP", "FCP", "TTFB", "INP" -> {
                // Log as a span
                logWebVitalDurationSpan(timestamp, value, level, fields, parentSpanId)
            }
            "CLS" -> {
                // Log as regular log
                logger.logInternal(LogType.UX, level, fields.toFields()) {
                    "webview.webVital"
                }
            }
            else -> {
                // Unknown metric type - log as regular log with UX type
                logger.logInternal(LogType.UX, level, fields.toFields()) {
                    "webview.webVital"
                }
            }
        }
    }

    private fun logWebVitalDurationSpan(
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
            logger.startSpan(
                name = "webview.webVital",
                level = level,
                fields = fields,
                startTimeMs = startTimeMs,
                parentSpanId = parentUuid,
            )
        span.end(result = result, fields = fields, endTimeMs = timestamp)
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

        logger.log(requestInfo)
        logger.log(responseInfo)
    }

    private fun handlePageView(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val action = msg.action ?: return
        val spanId = msg.spanId ?: return
        val fields = extractFields(msg)

        when (action) {
            "start" -> {
                currentPageSpanId = spanId

                val span =
                    logger.startSpan(
                        name = "webview.pageView",
                        level = LogLevel.DEBUG,
                        fields = fields,
                        startTimeMs = timestamp,
                    )
                activePageViewSpans[spanId] = span
            }
            "end" -> {
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
        val fields = extractFields(msg)
        logger.logInternal(LogType.UX, LogLevel.DEBUG, fields.toFields()) {
            "webview.lifecycle"
        }
    }

    private fun handleNavigation(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val fields = extractFields(msg)

        logger.log(LogLevel.DEBUG, fields.toFields()) {
            "webview.navigation"
        }
    }

    private fun handleError(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val fields = extractFields(msg)

        logger.log(LogLevel.ERROR, fields.toFields()) {
            "webview.error"
        }
    }

    private fun handleLongTask(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val durationMsDouble = msg.durationMs?.toDouble() ?: return

        val fields = extractFields(msg)

        val level =
            when {
                durationMsDouble >= 200 -> LogLevel.WARNING
                durationMsDouble >= 100 -> LogLevel.INFO
                else -> LogLevel.DEBUG
            }

        logger.logInternal(LogType.UX, level, fields.toFields()) {
            "webview.longTask"
        }
    }

    private fun handleResourceError(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val fields = extractFields(msg)

        logger.log(LogLevel.WARNING, fields.toFields()) {
            "webview.resourceError"
        }
    }

    private fun handleConsole(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val level = msg.level ?: "log"
        val fields = extractFields(msg)

        val logLevel =
            when (level) {
                "error" -> LogLevel.ERROR
                "warn" -> LogLevel.WARNING
                "info" -> LogLevel.INFO
                else -> LogLevel.DEBUG
            }

        logger.log(logLevel, fields.toFields()) {
            "webview.console"
        }
    }

    private fun handlePromiseRejection(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val fields = extractFields(msg)

        logger.log(LogLevel.ERROR, fields.toFields()) {
            "webview.promiseRejection"
        }
    }

    private fun handleUserInteraction(
        msg: WebViewBridgeMessage,
        timestamp: Long,
    ) {
        val interactionType = msg.interactionType ?: return
        val fields = extractFields(msg)

        val level = if (interactionType == "rageClick") LogLevel.WARNING else LogLevel.DEBUG
        logger.logInternal(LogType.UX, level, fields.toFields()) {
            "webview.userInteraction"
        }
    }
}
