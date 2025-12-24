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
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpRequestMetrics
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import java.net.URI

/**
 * Handles incoming messages from the WebView JavaScript bridge and routes them
 * to the appropriate logging methods.
 */
internal class WebViewMessageHandler(
    private val logger: ILogger?,
) {
    private val gson = Gson()

    fun handleMessage(message: String, capture: WebViewCapture) {
        val json = try {
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
            "error" -> handleError(json, timestamp)
        }
    }

    private fun handleBridgeReady(json: JsonObject, capture: WebViewCapture) {
        capture.onBridgeReady()
        
        val url = json.get("url")?.asString ?: ""
        logger?.log(LogLevel.DEBUG, mapOf("_url" to url)) {
            "WebView bridge ready"
        }
    }

    private fun handleWebVital(json: JsonObject, timestamp: Long) {
        val metric = json.getAsJsonObject("metric") ?: return
        val name = metric.get("name")?.asString ?: return
        val value = metric.get("value")?.asDouble ?: return
        val rating = metric.get("rating")?.asString ?: "unknown"
        val navigationType = metric.get("navigationType")?.asString
        val fields = buildMap {
            put("_metric", name)
            put("_value", value.toString())
            put("_rating", rating)
            navigationType?.let { put("_navigationType", it) }
            put("_source", "webview")
            put("_timestamp", timestamp.toString())
        }

        // Determine log level based on rating
        val level = when (rating) {
            "good" -> LogLevel.DEBUG
            "needs-improvement" -> LogLevel.INFO
            "poor" -> LogLevel.WARNING
            else -> LogLevel.DEBUG
        }

        logger?.log(level, fields) {
            "webview.webVital.$name"
        }
    }

    private fun handleNetworkRequest(json: JsonObject, timestamp: Long) {
        val method = json.get("method")?.asString ?: "GET"
        val url = json.get("url")?.asString ?: return
        val statusCode = json.get("statusCode")?.asInt
        val durationMs = json.get("durationMs")?.asLong ?: 0
        val success = json.get("success")?.asBoolean ?: false
        val errorMessage = json.get("error")?.asString
        val requestType = json.get("requestType")?.asString ?: "unknown"
        val timing = json.getAsJsonObject("timing")

        // Parse URL components
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            null
        }
        
        val host = uri?.host
        val path = uri?.path?.takeIf { it.isNotEmpty() }
        val query = uri?.query

        // Build extra fields for WebView-specific data
        val extraFields = buildMap {
            put("_source", "webview")
            put("_request_type", requestType)
            put("_timestamp", timestamp.toString())
        }

        // Create HttpRequestInfo
        val requestInfo = HttpRequestInfo(
            method = method,
            host = host,
            path = path?.let { HttpUrlPath(it) },
            query = query,
            extraFields = extraFields,
        )

        // Build metrics from timing data if available
        val metrics = timing?.let { t ->
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
        val result = when {
            success -> HttpResponse.HttpResult.SUCCESS
            errorMessage != null -> HttpResponse.HttpResult.FAILURE
            statusCode != null && statusCode >= 400 -> HttpResponse.HttpResult.FAILURE
            else -> HttpResponse.HttpResult.FAILURE
        }

        // Build error if present
        val error = errorMessage?.let { Exception(it) }

        // Create HttpResponseInfo
        val responseInfo = HttpResponseInfo(
            request = requestInfo,
            response = HttpResponse(
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

    private fun handleNavigation(json: JsonObject, timestamp: Long) {
        val fromUrl = json.get("fromUrl")?.asString ?: ""
        val toUrl = json.get("toUrl")?.asString ?: ""
        val method = json.get("method")?.asString ?: ""

        val fields = mapOf(
            "_fromUrl" to fromUrl,
            "_toUrl" to toUrl,
            "_method" to method,
            "_source" to "webview",
            "_timestamp" to timestamp.toString()
        )

        logger?.log(LogLevel.DEBUG, fields) {
            "webview.navigation"
        }
    }

    private fun handleError(json: JsonObject, timestamp: Long) {
        val errorMessage = json.get("message")?.asString ?: "Unknown error"
        val stack = json.get("stack")?.asString
        val filename = json.get("filename")?.asString
        val lineno = json.get("lineno")?.asInt
        val colno = json.get("colno")?.asInt

        val fields = buildMap {
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
}
