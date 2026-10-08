// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture_flutter

import android.content.Context
import android.os.Build
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
import io.bitdrift.capture.utils.SdkDirectory
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
                val key = call.argument<String>("key")!!
                val value = call.argument<String>("value")!!
                Logger.addField(key, value)
                result.success(null)
            }
            "removeField" -> {
                val key = call.argument<String>("key")!!
                Logger.removeField(key)
                result.success(null)
            }
            "setEntityId" -> {
                val entityId = call.argument<String>("entityId")!!
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
            "getReportContext" -> result.success(reportContext())
            "logAppLaunchTTI" -> {
                val durationMs = call.argument<Number>("durationMs")!!.toLong()
                Logger.logAppLaunchTTI(durationMs.milliseconds)
                result.success(null)
            }
            "logNetworkRequest" -> {
                Logger.log(call.arguments<Map<String, Any?>>()!!.toHttpRequestInfo())
                result.success(null)
            }
            "logNetworkResponse" -> {
                Logger.log(call.arguments<Map<String, Any?>>()!!.toHttpResponseInfo())
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
                val name = call.argument<String>("name")!!
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
        val apiKey = call.argument<String>("apiKey")!!
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
        val message = call.argument<String>("message")!!
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
        val screenName = call.argument<String>("screenName")!!
        Logger.logScreenView(screenName)
        result.success(null)
    }

    private fun handleStartSpan(call: MethodCall, result: MethodChannel.Result) {
        val name = call.argument<String>("name")!!
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
        val spanId = call.argument<String>("spanId")!!
        val success = call.argument<Boolean>("success") ?: true
        val span = activeSpans.remove(spanId)
        if (span != null) {
            if (success) span.end(SpanResult.SUCCESS) else span.end(SpanResult.FAILURE)
        }
        result.success(null)
    }

    private fun handleLogReplayScreen(call: MethodCall, result: MethodChannel.Result) {
        val screenBytes = call.argument<ByteArray>("screen")!!
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

    private fun reportContext(): Map<String, Any?> {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        return mapOf(
            "sdkDirectory" to SdkDirectory.getPath(context),
            "appId" to context.packageName,
            "appVersion" to packageInfo.versionName,
            "versionCode" to versionCode,
            "osVersion" to Build.VERSION.RELEASE,
            "osBrand" to Build.BRAND,
            "osFingerprint" to Build.FINGERPRINT,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "cpuAbis" to Build.SUPPORTED_ABIS.toList(),
        )
    }

    companion object {
        private val activeSpans = mutableMapOf<String, Span>()
    }
}

private fun String?.toSleepMode(): SleepMode = if (this == "enabled") SleepMode.ENABLED else SleepMode.DISABLED

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toHttpRequestInfo(): HttpRequestInfo {
    val path = this["path"] as String?
    return HttpRequestInfo(
        method = this["method"] as String,
        host = this["host"] as String?,
        path = path?.let { HttpUrlPath(it, this["pathTemplate"] as String?) },
        query = this["query"] as String?,
        headers = this["headers"] as Map<String, String>?,
        bytesExpectedToSendCount = (this["bytesExpectedToSendCount"] as Number?)?.toLong(),
        spanId = UUID.fromString(this["spanId"] as String),
        extraFields = this["extraFields"] as Map<String, String>? ?: emptyMap(),
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toHttpResponseInfo(): HttpResponseInfo {
    val extraFields =
        listOf(
            "_error_type" to this["errorType"],
            "_error_message" to this["errorMessage"],
            "_request_body_bytes_sent_count" to this["requestBodyBytesSentCount"],
            "_response_body_bytes_received_count" to this["responseBodyBytesReceivedCount"],
        ).mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()
    return HttpResponseInfo(
        request = (this["request"] as Map<String, Any?>).toHttpRequestInfo(),
        response =
            HttpResponse(
                result =
                    when (this["result"]) {
                        "success" -> HttpResponse.HttpResult.SUCCESS
                        "canceled" -> HttpResponse.HttpResult.CANCELED
                        else -> HttpResponse.HttpResult.FAILURE
                    },
                headers = this["headers"] as Map<String, String>?,
                statusCode = (this["statusCode"] as Number?)?.toInt(),
            ),
        durationMs = (this["durationMs"] as Number).toLong(),
        extraFields = extraFields + (this["extraFields"] as Map<String, String>? ?: emptyMap()),
    )
}
