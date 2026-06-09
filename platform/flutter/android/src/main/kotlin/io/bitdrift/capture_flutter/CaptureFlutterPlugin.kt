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
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.providers.toFieldValue
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Duration

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
            "startSpan" -> handleStartSpan(call, result)
            "endSpan" -> handleEndSpan(call, result)
            "logReplayScreen" -> handleLogReplayScreen(call, result)
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
                configuration = Configuration(
                    // Flutter provides its own wireframe data via logSessionReplayScreen.
                    sessionReplayConfiguration = null,
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

    companion object {
        private val activeSpans = mutableMapOf<String, Span>()
    }
}
