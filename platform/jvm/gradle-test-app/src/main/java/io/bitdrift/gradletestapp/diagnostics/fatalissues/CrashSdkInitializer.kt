// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.fatalissues

import android.app.Application
import androidx.preference.PreferenceManager
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.bugsnag.android.Event
import com.bugsnag.android.OnErrorCallback
import com.bugsnag.android.Severity
import io.bitdrift.capture.Capture
import io.bitdrift.gradletestapp.ui.compose.components.SettingsApiKeysDialogFragment.Companion.BUG_SNAG_SDK_API_KEY
import io.bitdrift.gradletestapp.ui.compose.components.SettingsApiKeysDialogFragment.Companion.SENTRY_SDK_DSN_KEY
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import org.json.JSONObject
import timber.log.Timber
import kotlin.time.measureTime

object CrashSdkInitializer {
    fun init(application: Application) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        val bugSnagApiKey = sharedPreferences.getString(BUG_SNAG_SDK_API_KEY, "")
        if (!bugSnagApiKey.isNullOrEmpty()) {
            val bugSnagStartTime =
                measureTime {
                    val bugsnagConfiguration =
                        Configuration(bugSnagApiKey).apply {
                            addOnError { event ->
                                event.emitBugsnagErrorBeforeSend()
                                true
                            }
                        }
                    Bugsnag.start(application, bugsnagConfiguration)

                    val bugsnagLastRunInfo = Bugsnag.getLastRunInfo()
                    bugsnagLastRunInfo?.let { lastRunInfo ->
                        Capture.Logger.logInfo(
                            mapOf("crashed" to lastRunInfo.crashed.toString(),
                                "crashedDuringLaunch" to lastRunInfo.crashedDuringLaunch.toString(),
                                "consecutiveLaunchCrashes" to lastRunInfo.consecutiveLaunchCrashes.toString()
                            )
                        ) {
                            "Bugsnag LastRunInfo"
                        }
                    }

                }
            Timber.i("Bugsnag.start() took ${bugSnagStartTime.inWholeMilliseconds} ms")
            reportNonFatalIssue()
        }

        val sentryKey = sharedPreferences.getString(SENTRY_SDK_DSN_KEY, "")
        if (!sentryKey.isNullOrEmpty()) {
            val sentryStartTime =
                measureTime {
                    SentryAndroid.init(application) { options: SentryAndroidOptions ->
                        options.dsn = sentryKey
                        options.isEnabled = true
                        options.setBeforeSend { event, _ ->
                            event.emitSentryErrorBeforeSend()
                            event
                        }
                    }
                }
            Timber.i("SentryAndroid.init() took ${sentryStartTime.inWholeMilliseconds} ms")
        }
    }

    private fun reportNonFatalIssue() {
        Bugsnag.notify(IllegalStateException("Fake non-fatal error"), object : OnErrorCallback {
            override fun onError(event: Event): Boolean {
                event.addMetadata("section_bugsnag", "key_bugsnag", "value_bugsnag")
                return false
            }
        })
    }

    private fun Event.emitBugsnagErrorBeforeSend() {
        val firstError = errors.firstOrNull()
        val firstThread = threads.firstOrNull()
        val groupingHash = groupingHash ?: "empty_group_hash"
        val eventData =
            mapOf(
                "severity" to severity.toString(),
                "unhandled" to isUnhandled.toString(),
            )

        val errorData =
            mapOf(
                "class" to (firstError?.errorClass ?: "empty_error_class"),
                "error_count" to errors.size.toString(),
                "message" to (firstError?.errorMessage ?: "empty_error_message"),
                "type" to (firstError?.type?.toString() ?: "empty_error_type"),
                "stack_size" to (firstError?.stacktrace?.size?.toString() ?: "0"),
                "thread_id" to (firstThread?.id ?: "empty_thread_id"),
                "thread_name" to (firstThread?.name ?: "empty_thread_name"),
                "thread_type" to (firstThread?.type?.toString() ?: "empty_thread_type"),
                "thread_count" to threads.size.toString(),
            )

        val appData =
            mapOf(
                "id" to (app.id ?: "empty_app_id"),
                "version" to (app.version ?: "empty_app_version"),
                "release_stage" to (app.releaseStage ?: "empty_release_stage"),
                "duration_ms" to (app.duration?.toString() ?: "empty_duration"),
                "in_foreground" to (app.inForeground?.toString() ?: "empty_in_foreground"),
            )

        val deviceData =
            mapOf(
                "id" to (device.id ?: "empty_device_id"),
                "model" to (device.model ?: "empty_device_model"),
                "manufacturer" to (device.manufacturer ?: "empty_device_manufacturer"),
                "os_name" to (device.osName ?: "empty_os_name"),
                "os_version" to (device.osVersion ?: "empty_os_version"),
                "orientation" to (device.orientation ?: "empty_orientation"),
            )

        val fields =
            mapOf(
                "grouping_hash" to groupingHash,
                "context" to (context ?: "empty_context"),
                "bugsnag_event" to JSONObject(eventData).toString(),
                "bugsnag_error" to JSONObject(errorData).toString(),
                "bugsnag_app" to JSONObject(appData).toString(),
                "bugsnag_device" to JSONObject(deviceData).toString(),
            )

        val message = "Bugsnag OnErrorCallback triggered. See fields details"
        when (severity) {
            Severity.INFO -> Capture.Logger.logInfo(fields = fields) { message }
            Severity.WARNING -> Capture.Logger.logWarning(fields = fields) { message }
            Severity.ERROR -> Capture.Logger.logError(fields = fields) { message }
        }
    }

    private fun SentryEvent.emitSentryErrorBeforeSend() {
        val sentryEventData =
            mapOf(
                "id" to (eventId?.toString() ?: "empty_event_id"),
                "level" to (level?.name ?: "empty_level"),
                "logger" to (logger ?: "empty_logger"),
                "transaction" to (transaction ?: "empty_transaction"),
            )
        val sentryErrorData =
            mapOf(
                "throwable_class" to (throwable?.javaClass?.name ?: "empty_throwable_class"),
                "throwable_message" to (throwable?.message ?: "empty_throwable_message"),
            )

        val fields =
            mapOf(
                "sentry_event" to JSONObject(sentryEventData).toString(),
                "sentry_error" to JSONObject(sentryErrorData).toString(),
            )

        val message = "Sentry beforeSend triggered. See fields details"
        Capture.Logger.logInfo(fields = fields) { message }
    }
}
