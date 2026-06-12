// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.bdreactnative

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.bitdrift.capture.Capture
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.InitializationState
import io.bitdrift.capture.SdkStatus
import io.bitdrift.capture.providers.session.SessionStrategy
import com.facebook.react.bridge.Promise
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.reports.IssueCallbackConfiguration
import io.bitdrift.capture.reports.IssueReportCallback
import io.bitdrift.capture.webview.WebViewConfiguration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BdReactNativeModule internal constructor(context: ReactApplicationContext) :
  BdReactNativeSpec(context) {

  private val issueCallbackExecutor: ExecutorService by lazy {
    buildCallbackExecutor()
  }

  private val debugId: String? by lazy {
    DebugId.fromBundle(reactApplicationContext.assets)
  }

  override fun getName(): String {
    return NAME
  }

  @ReactMethod
  override fun init(key: String, sessionStrategy: String, options: ReadableMap?) {
    val apiUrl = options?.getString("url") ?: "https://api.bitdrift.io"
    val crashReportingOptions = options.getMapOrNull("crashReporting")
    val webViewOptions = options.getMapOrNull("webView")
    val enableNativeFatalIssues = crashReportingOptions.getBooleanOrDefault("enableNativeFatalIssues", true)
    val enableJsErrors = crashReportingOptions.getBooleanOrDefault("UNSTABLE_enableJsErrors", false)
    val enableIssueCallbackBridge = crashReportingOptions.getBooleanOrDefault("enableIssueCallbackBridge", false)
    val enableStartResultBridge = options.getBooleanOrDefault("enableStartResultBridge", false)

    ReportDirectory.setupWatcherDirectory(reactApplicationContext, enableJsErrors)

    val strategy =
    when (sessionStrategy) {
      "fixed" -> SessionStrategy.Fixed()
      "activity" -> SessionStrategy.ActivityBased()
      else -> throw IllegalArgumentException("Invalid session strategy: $sessionStrategy")
    }

    val configuration = Configuration(
      enableFatalIssueReporting = enableNativeFatalIssues,
      webViewConfiguration = buildWebViewConfiguration(webViewOptions),
      issueCallbackConfiguration = buildIssueCallbackConfiguration(enableIssueCallbackBridge),
    )

    Capture.Logger.start(
      apiKey = key,
      apiUrl = apiUrl.toHttpUrl(),
      sessionStrategy = strategy,
      configuration = configuration,
      context = reactApplicationContext,
      startResult = if (enableStartResultBridge) {
        { result -> emitStartResult(result) }
      } else {
        null
      },
    )
  }

  private fun buildCallbackExecutor(): ExecutorService {
    return Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, ISSUE_CALLBACK_THREAD_NAME)
    }   
  }

  private fun buildIssueCallbackConfiguration(enableIssueCallbackBridge: Boolean): IssueCallbackConfiguration?{
    return if (enableIssueCallbackBridge) {
      IssueCallbackConfiguration(
        executor = issueCallbackExecutor,
        issueReportCallback =
          IssueReportCallback { report ->
            emitIssueReport(report)
          },
      )
    } else {
      null
    }
  }

  @OptIn(ExperimentalBitdriftApi::class)
  private fun buildWebViewConfiguration(webViewOptions: ReadableMap?): WebViewConfiguration? {
    if (webViewOptions == null) {
      return null
    }

    return WebViewConfiguration(
      capturePageViews = webViewOptions.getBooleanOrDefault("capturePageViews", false),
      captureNetworkRequests = webViewOptions.getBooleanOrDefault("captureNetworkRequests", false),
      captureNavigationEvents = webViewOptions.getBooleanOrDefault("captureNavigationEvents", false),
      captureWebVitals = webViewOptions.getBooleanOrDefault("captureWebVitals", false),
      captureLongTasks = webViewOptions.getBooleanOrDefault("captureLongTasks", false),
      captureConsoleLogs = webViewOptions.getBooleanOrDefault("captureConsoleLogs", false),
      captureUserInteractions = webViewOptions.getBooleanOrDefault("captureUserInteractions", false),
      captureErrors = webViewOptions.getBooleanOrDefault("captureErrors", false),
    )
  }
  
  private fun emitIssueReport(report: io.bitdrift.capture.reports.Report) {
    if (!reactApplicationContext.hasActiveCatalystInstance()) {
      return
    }

    reactApplicationContext.runOnUiQueueThread {
      emitIssueReportPayload(report)
    }
  }

  private fun emitIssueReportPayload(report: io.bitdrift.capture.reports.Report) {
    if (!reactApplicationContext.hasActiveCatalystInstance()) {
      return
    }

    val payload = toWritableIssueReport(report)

    reactApplicationContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(ISSUE_REPORT_EVENT, payload)
  }

  private fun emitStartResult(result: CaptureResult<io.bitdrift.capture.ILogger>) {
    if (!reactApplicationContext.hasActiveCatalystInstance()) {
      return
    }

    reactApplicationContext.runOnUiQueueThread {
      if (!reactApplicationContext.hasActiveCatalystInstance()) {
        return@runOnUiQueueThread
      }

      reactApplicationContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(START_RESULT_EVENT, toWritableStartResult(result))
    }
  }

  private fun toWritableIssueReport(report: io.bitdrift.capture.reports.Report): WritableMap {
    val payload = Arguments.createMap()
    payload.putString("reportType", report.reportType)
    payload.putString("reason", report.reason)
    payload.putString("details", report.details)
    payload.putString("sessionId", report.sessionId)

    val fieldsMap = Arguments.createMap()
    for ((key, value) in report.fields) {
      fieldsMap.putString(key, value)
    }
    payload.putMap("fields", fieldsMap)

    return payload
  }

  private fun toWritableStartResult(result: CaptureResult<io.bitdrift.capture.ILogger>): WritableMap {
    val payload = Arguments.createMap()
    when (result) {
      is CaptureResult.Success -> {
        payload.putBoolean("success", true)
      }
      is CaptureResult.Failure -> {
        payload.putBoolean("success", false)
        payload.putString("error", result.error.message)
      }
    }

    return payload
  }

  @ReactMethod
  override fun getDeviceID(promise: Promise) {
    val deviceId = Capture.Logger.deviceId
    if (deviceId != null) {
        promise.resolve(deviceId)
    } else {
        promise.reject("device_id_undefined", "Device ID is undefined")
    }
  }

  @ReactMethod
  override fun getSessionID(promise: Promise) {
    val sessionId = Capture.Logger.sessionId
    if (sessionId != null) {
      promise.resolve(sessionId)
    } else {
      promise.reject("session_id_undefined", "Session ID is undefined")
    }
  }

  @ReactMethod
  override fun getSessionURL(promise: Promise) {
    val sessionUrl = Capture.Logger.sessionUrl
    if (sessionUrl != null) {
      promise.resolve(sessionUrl)
    } else {
      promise.reject("session_url_undefined", "Session URL is undefined")
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  override fun getSdkStatus(): WritableMap {
    return toWritableSdkStatus(Capture.Logger.getSdkStatus())
  }

  @OptIn(ExperimentalBitdriftApi::class)
  @ReactMethod(isBlockingSynchronousMethod = true)
  override fun getPreviousRunInfo(): WritableMap? {
    val info = Capture.Logger.getPreviousRunInfo() ?: return null
    val map = Arguments.createMap()
    map.putBoolean("hasFatallyTerminated", info.hasFatallyTerminated)
    info.terminationReason?.let { map.putString("terminationReason", it.name) }
    return map
  }

  @ReactMethod
  override fun log(level: Double, message: String, jsFields: ReadableMap?) {
    val fields = jsFields?.toHashMap()?.mapValues { it.value.toString() } ?: emptyMap()

    when (level) {
      0.0 -> Capture.Logger.logTrace(fields) { message }
      1.0 -> Capture.Logger.logDebug(fields) { message }
      2.0 -> Capture.Logger.logInfo(fields) { message }
      3.0 -> Capture.Logger.logWarning(fields) { message }
      4.0 -> Capture.Logger.logError(fields) { message }
    }
  }

  @ReactMethod
  override fun addField(key: String, value: String) {
    Capture.Logger.addField(key, value)
  }

  @ReactMethod
  override fun removeField(key: String) {
    Capture.Logger.removeField(key)
  }

  @ReactMethod
  override fun logScreenView(screenView: String) {
    Capture.Logger.logScreenView(screenView)
  }

  @ReactMethod
  override fun logAppLaunchTTI(ttiMs: Double) {
    Capture.Logger.logAppLaunchTTI(ttiMs.toDuration(DurationUnit.MILLISECONDS))
  }

  @ReactMethod
  override fun reportJsError(
    errorName: String,
    message: String,
    stack: String,
    isFatal: Boolean,
    engine: String,
    libraryVersion: String,
  ) {    
    Capture.Logger.persistJavaScriptReport(
      errorName,
      message,
      stack,
      isFatal,
      engine,
      this.debugId ?: "",
      libraryVersion,
    )
  }

  @ReactMethod
  override fun setFeatureFlagExposureString(name: String, variant: String) {
    Capture.Logger.setFeatureFlagExposure(name, variant)
  }

  @ReactMethod
  override fun setFeatureFlagExposureBool(name: String, variant: Boolean) {
    Capture.Logger.setFeatureFlagExposure(name, variant)
  }

  @ReactMethod
  override fun addListener(eventName: String) {
    // Required by the React Native event emitter/TurboModule contract.
    // No-op on Android; this module emits via DeviceEventEmitter.
  }

  @ReactMethod
  override fun removeListeners(count: Double) {
    // Required by the React Native event emitter/TurboModule contract.
    // No-op on Android; this module emits via DeviceEventEmitter.
  }

  companion object {
    const val NAME = "BdReactNative"
    // Must match src/index.tsx ISSUE_REPORT_EVENT and iOS equivalents.
    private const val ISSUE_REPORT_EVENT = "BdReactNative.onBeforeReportSend"
    private const val START_RESULT_EVENT = "BdReactNative.onStartResult"
    private const val ISSUE_CALLBACK_THREAD_NAME = "io.bitdrift.capture.reactnative.issue-callback"

    private fun toWritableSdkStatus(status: SdkStatus): WritableMap {
      val map = Arguments.createMap()
      map.putString("initializationState", status.initializationState.toJsValue())
      status.lastHandshakeTimeMs?.let { map.putDouble("lastHandshakeTimeMs", it.toDouble()) }
      status.lastConfigDeliveryTimeMs?.let { map.putDouble("lastConfigDeliveryTimeMs", it.toDouble()) }
      return map
    }

    private fun InitializationState.toJsValue(): String =
      when (this) {
        InitializationState.NOT_STARTED -> "notStarted"
        InitializationState.LOADED -> "loaded"
        InitializationState.RUNNING -> "running"
        InitializationState.DISABLED -> "disabled"
      }

    private fun ReadableMap?.getMapOrNull(key: String): ReadableMap? =
      this
        ?.takeIf { it.hasKey(key) && !it.isNull(key) }
        ?.let { map -> runCatching { map.getMap(key) }.getOrNull() }

    private fun ReadableMap?.getBooleanOrDefault(key: String, defaultValue: Boolean): Boolean =
      this
        ?.takeIf { it.hasKey(key) && !it.isNull(key) }
        ?.let { map -> runCatching { map.getBoolean(key) }.getOrDefault(defaultValue) }
        ?: defaultValue
  }
}
