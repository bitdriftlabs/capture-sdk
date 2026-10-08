// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture_flutter

import android.content.Context
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.SleepMode
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.providers.toFieldValue
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class CaptureFlutterPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "io.bitdrift.capture_flutter")
        channel.setMethodCallHandler(this)
        context = binding.applicationContext
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            dispatch(call, result)
        } catch (e: InvalidArgumentException) {
            result.error("INVALID_ARGS", e.message, null)
        } catch (e: ClassCastException) {
            result.error("INVALID_ARGS", e.message, null)
        }
    }

    private fun dispatch(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "start" -> handleStart(call, result)
            "log" -> handleLog(call, result)
            "logScreenView" -> handleLogScreenView(call, result)
            "getSessionId" -> result.success(Logger.sessionId)
            "getSessionUrl" -> result.success(Logger.sessionUrl)
            "getDeviceId" -> result.success(Logger.deviceId)
            "createTemporaryDeviceCode" -> handleCreateTemporaryDeviceCode(result)
            "startNewSession" -> {
                Logger.startNewSession()
                result.success(null)
            }
            "getSdkStatus" -> {
                val status = Logger.getSdkStatus()
                result.success(mapOf(
                    "initializationState" to status.initializationState.name,
                    "lastHandshakeTimeMs" to status.lastHandshakeTimeMs,
                    "lastConfigDeliveryTimeMs" to status.lastConfigDeliveryTimeMs,
                ))
            }
            "addField" -> {
                val key = call.requireArgument<String>("key")
                val value = call.requireArgument<String>("value")
                Logger.addField(key, value)
                result.success(null)
            }
            "removeField" -> {
                val key = call.requireArgument<String>("key")
                Logger.removeField(key)
                result.success(null)
            }
            "setEntityId" -> {
                val entityId = call.requireArgument<String>("entityId")
                Logger.setEntityId(entityId)
                result.success(null)
            }
            "clearEntityId" -> {
                Logger.clearEntityId()
                result.success(null)
            }
            "startSpan" -> handleStartSpan(call, result)
            "endSpan" -> handleEndSpan(call, result)
            "logReplayScreen" -> handleLogReplayScreen(call, result)
            "logAppLaunchTTI" -> {
                val durationMs = call.requireArgument<Number>("durationMs").toLong()
                Logger.logAppLaunchTTI(durationMs.milliseconds)
                result.success(null)
            }
            "logNetworkRequest" -> {
                Logger.log(call.requireArguments().toHttpRequestInfo())
                result.success(null)
            }
            "logNetworkResponse" -> {
                Logger.log(call.requireArguments().toHttpResponseInfo())
                result.success(null)
            }
            "getPreviousRunInfo" -> {
                val info = Logger.getPreviousRunInfo()
                result.success(
                    info?.let {
                        mapOf(
                            "hasFatallyTerminated" to it.hasFatallyTerminated,
                            "terminationReason" to it.terminationReason?.name,
                        )
                    },
                )
            }
            "setSleepMode" -> {
                Logger.setSleepMode(call.argument<String>("mode").toSleepMode())
                result.success(null)
            }
            "setFeatureFlagExposure" -> {
                val name = call.requireArgument<String>("name")
                when (val variant = call.argument<Any>("variant")) {
                    is Boolean -> Logger.setFeatureFlagExposure(name, variant)
                    else -> Logger.setFeatureFlagExposure(name, variant.toString())
                }
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun handleStart(call: MethodCall, result: MethodChannel.Result) {
        val apiKey = call.requireArgument<String>("apiKey")
        val apiUrl = call.argument<String>("apiUrl") ?: "https://api.bitdrift.io"
        val strategyName = call.argument<String>("sessionStrategy") ?: "fixed"
        val sessionStrategy = when (strategyName) {
            "activityBased" -> SessionStrategy.ActivityBased()
            else -> SessionStrategy.Fixed()
        }
        try {
            Logger.start(
                apiKey = apiKey,
                sessionStrategy = sessionStrategy,
                initialFields = call.argument<Map<String, String>>("initialFields") ?: emptyMap(),
                configuration = Configuration(
                    // Flutter provides its own wireframe data via logSessionReplayScreen.
                    sessionReplayConfiguration = null,
                    enableFatalIssueReporting = call.argument<Boolean>("enableFatalIssueReporting") ?: true,
                    sleepMode = call.argument<String>("sleepMode").toSleepMode(),
                ),
                apiUrl = apiUrl.toHttpUrl(),
                context = context,
            )
            result.success(true)
        } catch (e: Exception) {
            result.success(false)
        }
    }

    private fun handleLog(call: MethodCall, result: MethodChannel.Result) {
        val level = when (call.argument<String>("level")) {
            "trace" -> LogLevel.TRACE
            "debug" -> LogLevel.DEBUG
            "info" -> LogLevel.INFO
            "warning" -> LogLevel.WARNING
            "error" -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        val message = call.requireArgument<String>("message")
        val fields = call.argument<Map<String, String>>("fields") ?: emptyMap()
        Logger.log(level, fields) { message }
        result.success(null)
    }

    private fun handleCreateTemporaryDeviceCode(result: MethodChannel.Result) {
        Logger.createTemporaryDeviceCode { captureResult ->
            when (captureResult) {
                is CaptureResult.Success -> result.success(captureResult.value)
                is CaptureResult.Failure -> result.error(
                    "DEVICE_CODE_ERROR",
                    captureResult.error.message,
                    null,
                )
            }
        }
    }

    private fun handleLogScreenView(call: MethodCall, result: MethodChannel.Result) {
        val screenName = call.requireArgument<String>("screenName")
        Logger.logScreenView(screenName)
        result.success(null)
    }

    private fun handleStartSpan(call: MethodCall, result: MethodChannel.Result) {
        val name = call.requireArgument<String>("name")
        val level = when (call.argument<String>("level")) {
            "trace" -> LogLevel.TRACE
            "debug" -> LogLevel.DEBUG
            "warning" -> LogLevel.WARNING
            "error" -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        val fields = call.argument<Map<String, String>>("fields") ?: emptyMap()
        val span = Logger.startSpan(name, level, fields)
        if (span != null) {
            val spanId = span.hashCode().toString()
            activeSpans[spanId] = span
            result.success(spanId)
        } else {
            result.success(null)
        }
    }

    private fun handleEndSpan(call: MethodCall, result: MethodChannel.Result) {
        val spanId = call.requireArgument<String>("spanId")
        val success = call.argument<Boolean>("success") ?: true
        val span = activeSpans.remove(spanId)
        if (span != null) {
            if (success) span.end(SpanResult.SUCCESS) else span.end(SpanResult.FAILURE)
        }
        result.success(null)
    }

    private fun handleLogReplayScreen(call: MethodCall, result: MethodChannel.Result) {
        val screenBytes = call.requireArgument<ByteArray>("screen")
        val duration = call.argument<Double>("duration") ?: 0.0
        // logSessionReplayScreen is on the internal IInternalLogger interface.
        // No public API exists for this yet — requires @file:Suppress to access.
        // Track: make logSessionReplayScreen public on ILogger or Capture.Logger.
        try {
            val logger = Capture.logger() as? IInternalLogger
            if (logger != null) {
                val fields = arrayOf(
                    Field("screen", screenBytes.toFieldValue()),
                )
                logger.logSessionReplayScreen(fields, Duration.ZERO)
            }
            result.success(null)
        } catch (e: Exception) {
            result.error("REPLAY_ERROR", e.message, null)
        }
    }

    companion object {
        private val activeSpans = mutableMapOf<String, Span>()
    }
}

private fun String?.toSleepMode(): SleepMode = if (this == "enabled") SleepMode.ENABLED else SleepMode.DISABLED

private class InvalidArgumentException(
    message: String,
) : IllegalArgumentException(message)

private inline fun <reified T> MethodCall.requireArgument(key: String): T =
    argument<Any>(key) as? T ?: throw InvalidArgumentException("Missing or invalid argument: $key")

@Suppress("UNCHECKED_CAST")
private fun MethodCall.requireArguments(): Map<String, Any?> =
    arguments as? Map<String, Any?> ?: throw InvalidArgumentException("Missing arguments")

private inline fun <reified T> Map<String, Any?>.require(key: String): T =
    this[key] as? T ?: throw InvalidArgumentException("Missing or invalid argument: $key")

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.stringMap(key: String): Map<String, String>? = this[key] as? Map<String, String>

private fun Map<String, Any?>.toHttpRequestInfo(): HttpRequestInfo {
    val spanId =
        runCatching { UUID.fromString(require<String>("spanId")) }
            .getOrElse { throw InvalidArgumentException("Invalid argument: spanId") }
    return HttpRequestInfo(
        method = require("method"),
        host = this["host"] as? String,
        path = (this["path"] as? String)?.let { HttpUrlPath(it, this["pathTemplate"] as? String) },
        query = this["query"] as? String,
        headers = stringMap("headers"),
        bytesExpectedToSendCount = (this["bytesExpectedToSendCount"] as? Number)?.toLong(),
        spanId = spanId,
        extraFields = stringMap("extraFields") ?: emptyMap(),
    )
}

private fun Map<String, Any?>.toHttpResponseInfo(): HttpResponseInfo {
    val extraFields =
        listOf(
            "_error_type" to this["errorType"],
            "_error_message" to this["errorMessage"],
            "_request_body_bytes_sent_count" to this["requestBodyBytesSentCount"],
            "_response_body_bytes_received_count" to this["responseBodyBytesReceivedCount"],
        ).mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()
    return HttpResponseInfo(
        request = require<Map<String, Any?>>("request").toHttpRequestInfo(),
        response =
            HttpResponse(
                result =
                    when (this["result"]) {
                        "success" -> HttpResponse.HttpResult.SUCCESS
                        "canceled" -> HttpResponse.HttpResult.CANCELED
                        else -> HttpResponse.HttpResult.FAILURE
                    },
                headers = stringMap("headers"),
                statusCode = (this["statusCode"] as? Number)?.toInt(),
            ),
        durationMs = require<Number>("durationMs").toLong(),
        extraFields = extraFields + (stringMap("extraFields") ?: emptyMap()),
    )
}
