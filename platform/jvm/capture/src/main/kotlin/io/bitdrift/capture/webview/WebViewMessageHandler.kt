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
import io.bitdrift.capture.events.span.SpanResult

/**
 * Handles incoming messages from the WebView JavaScript bridge and routes them
 * to the appropriate logging methods.
 */
internal class WebViewMessageHandler(
    private val logger: ILogger?,
) {
    private val gson = Gson()

    // Track ongoing network request spans by requestId
    private val networkSpans = mutableMapOf<String, io.bitdrift.capture.events.span.Span>()

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
        val statusCode = json.get("statusCode")?.asInt ?: 0
        val durationMs = json.get("durationMs")?.asLong ?: 0
        val success = json.get("success")?.asBoolean ?: false
        val error = json.get("error")?.asString
        val requestType = json.get("requestType")?.asString ?: "unknown"

        // Extract timing data if available
        val timing = json.getAsJsonObject("timing")
        
        val fields = buildMap {
            put("_url", url)
            put("_method", method)
            put("_statusCode", statusCode.toString())
            put("_durationMs", durationMs.toString())
            put("_requestType", requestType)
            put("_source", "webview")
            put("_timestamp", timestamp.toString())
            error?.let { put("_error", it) }
            
            timing?.let { t ->
                t.get("dnsMs")?.asDouble?.let { put("_dnsMs", it.toString()) }
                t.get("connectMs")?.asDouble?.let { put("_connectMs", it.toString()) }
                t.get("tlsMs")?.asDouble?.let { put("_tlsMs", it.toString()) }
                t.get("ttfbMs")?.asDouble?.let { put("_ttfbMs", it.toString()) }
                t.get("downloadMs")?.asDouble?.let { put("_downloadMs", it.toString()) }
                t.get("transferSize")?.asLong?.let { put("_transferSize", it.toString()) }
            }
        }

        val level = if (success) LogLevel.DEBUG else LogLevel.WARNING
        
        logger?.log(level, fields) {
            "webview.network"
        }
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
