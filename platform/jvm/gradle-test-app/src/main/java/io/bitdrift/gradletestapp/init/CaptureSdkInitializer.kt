// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.bugsnag.android.Bugsnag
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.bitdrift.capture.Capture
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.InitializationState
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.reports.IssueCallbackConfiguration
import io.bitdrift.capture.reports.IssueReportCallback
import io.bitdrift.capture.reports.Report
import io.bitdrift.capture.webview.WebViewConfiguration
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_ENABLE_CONSOLE_LOGS_KEY
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_ENABLE_ERRORS_KEY
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_ENABLE_LONG_TASKS_KEY
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_ENABLE_NAVIGATION_EVENTS_KEY
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_ENABLE_NETWORK_REQUESTS_KEY
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_ENABLE_PAGE_VIEWS_KEY
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_ENABLE_USER_INTERACTIONS_KEY
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_ENABLE_WEB_VITALS_KEY
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_MONITORING_ENABLED_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.BITDRIFT_API_KEY
import io.sentry.Sentry
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Starts bitdrift's Captures SDK with the persisted config settings
 */
object CaptureSdkInitializer {
    private val userUuid = UUID.randomUUID().toString()
    private val bitdriftSessionUrlKey = "bitdrift_session_url"

    /**
     * Init sdk with the persisted settings
     */
    @OptIn(ExperimentalBitdriftApi::class)
    fun initFromPreferences(
        applicationContext: Context,
        sharedPreferences: SharedPreferences,
    ): Boolean {

        val persistedSdkConfigResult = getPersistedCaptureSdkSettings(
            applicationContext,
            sharedPreferences,
        )

        return when (persistedSdkConfigResult) {

            is PersistedSdkConfigResult.Success -> {
                startCaptureSdk(persistedSdkConfigResult.captureSdkInitSettings, applicationContext)
                logPreviousRunInfoToBitdrift()
                return Capture.Logger.getSdkStatus().initializationState != InitializationState.NOT_STARTED
            }

            is PersistedSdkConfigResult.Failed -> {
                Timber.i(persistedSdkConfigResult.message)
                false
            }
        }
    }

    @SuppressLint("LogNotTimber")
    @ExperimentalBitdriftApi
    private fun startCaptureSdk(
        settings: CaptureSdkInitSettings,
        context: Context,
    ) {

        Capture.Logger.start(
            apiKey = settings.apiKey,
            apiUrl = settings.apiUrl,
            configuration = settings.configuration,
            sessionStrategy = settings.sessionStrategy,
            fieldProviders = settings.fieldProviders,
            context = context,
        ) { startResult ->
            when (startResult) {
                is CaptureResult.Success -> {
                    val logger = startResult.value
                    Log.d("bitdrift","SDK started successfully. sessionId=${logger.sessionId}, sessionUrl=${logger.sessionUrl}, userUuid=${userUuid}")
                    Capture.Logger.setEntityId(userUuid)
                    addSessionUrlToThirdPartySdks(context, logger.sessionUrl)
                }

                is CaptureResult.Failure -> {
                    Log.d("bitdrift","SDK failed to start: ${startResult.error.message}")
                    // Re-throwing on debug builds so we can get immediate signal of
                    // any issues at Capture.Logger.start internals during the development phase.
                    throw IllegalStateException(startResult.error.message)
                }
            }
        }
    }

    private fun getPersistedCaptureSdkSettings(
        applicationContext: Context,
        sharedPreferences: SharedPreferences,
    ): PersistedSdkConfigResult {
        val apiKey =
            sharedPreferences.getString(
                BITDRIFT_API_KEY,
                null,
            )
        val apiUrl = sharedPreferences.getString("apiUrl", null)?.toHttpUrlOrNull()
        if (apiUrl == null || apiKey.isNullOrBlank()) {
            return PersistedSdkConfigResult.Failed(
                "Invalid settings. apiUrl: $apiUrl. apiKey configured: ${!apiKey.isNullOrBlank()}",
            )
        }
        val fatalIssueReporterEnabled =
            sharedPreferences.getBoolean(
                ConfigurationSettingsFragment.Companion.FATAL_ISSUE_ENABLED_PREFS_KEY,
                true,
            )
        val sessionReplayEnabled =
            sharedPreferences.getBoolean(
                ConfigurationSettingsFragment.Companion.SESSION_REPLAY_ENABLED_PREFS_KEY,
                true,
            )

        val sessionStrategy = getSessionStrategy(applicationContext, sharedPreferences)
        val webViewConfig = getWebViewConfiguration(sharedPreferences)

        @OptIn(ExperimentalBitdriftApi::class)
        val issueCallbackConfiguration = IssueCallbackConfiguration(
            executor = buildIssueReportCallbackExecutor(),
            issueReportCallback = CustomerIssueReportCallback(),
        )

        val configuration =
            Configuration(
                sessionReplayConfiguration = if (sessionReplayEnabled) SessionReplayConfiguration() else null,
                enableFatalIssueReporting = fatalIssueReporterEnabled,
                issueCallbackConfiguration = issueCallbackConfiguration,
                webViewConfiguration = webViewConfig,
            )
        val fieldProviders = listOf(FieldProvider {
            mapOf("user_id" to userUuid)
        })

        val captureSdkInitSettings =
            CaptureSdkInitSettings(
                apiUrl = apiUrl,
                apiKey = apiKey,
                sessionStrategy = sessionStrategy,
                configuration = configuration,
                fieldProviders = fieldProviders,
            )
        return PersistedSdkConfigResult.Success(captureSdkInitSettings)
    }

    private fun SharedPreferences.getPersistedFlag(keyName: String): Boolean = getBoolean(
        keyName,
        false
    )

    private fun getSessionStrategy(
        applicationContext: Context,
        sharedPreferences: SharedPreferences
    ): SessionStrategy =
        if (sharedPreferences.getString(
                ConfigurationSettingsFragment.Companion.SESSION_STRATEGY_PREFS_KEY,
                ConfigurationSettingsFragment.SessionStrategyPreferences.FIXED.displayName,
            ) == "Fixed"
        ) {
            SessionStrategy.Fixed()
        } else {
            val thresholdMins = sharedPreferences.getString(
                ConfigurationSettingsFragment.INACTIVITY_THRESHOLD_PREFS_KEY,
                "30",
            )?.toLongOrNull() ?: 30L
            SessionStrategy.ActivityBased(
                inactivityThresholdMins = thresholdMins,
                onSessionIdChanged = { sessionId ->
                    val message =
                        "Bitdrift Logger session id updated due to inactivity: $sessionId. Callback triggered in ${Thread.currentThread().name} thread"
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    Timber.Forest.i(message)
                },
            )
        }

    private fun getWebViewConfiguration(sharedPrefs: SharedPreferences): WebViewConfiguration? {
        if (!sharedPrefs.getBoolean(WEBVIEW_MONITORING_ENABLED_KEY, false)
        ) {
            return null
        }

        @OptIn(ExperimentalBitdriftApi::class)
        return WebViewConfiguration(
            captureConsoleLogs = sharedPrefs.getPersistedFlag(WEBVIEW_ENABLE_CONSOLE_LOGS_KEY),
            captureErrors = sharedPrefs.getPersistedFlag(WEBVIEW_ENABLE_ERRORS_KEY),
            captureNetworkRequests = sharedPrefs.getPersistedFlag(
                WEBVIEW_ENABLE_NETWORK_REQUESTS_KEY
            ),
            captureNavigationEvents = sharedPrefs.getPersistedFlag(
                WEBVIEW_ENABLE_NAVIGATION_EVENTS_KEY
            ),
            capturePageViews = sharedPrefs.getPersistedFlag(WEBVIEW_ENABLE_PAGE_VIEWS_KEY),
            captureWebVitals = sharedPrefs.getPersistedFlag(WEBVIEW_ENABLE_WEB_VITALS_KEY),
            captureLongTasks = sharedPrefs.getPersistedFlag(WEBVIEW_ENABLE_LONG_TASKS_KEY),
            captureUserInteractions = sharedPrefs.getPersistedFlag(
                WEBVIEW_ENABLE_USER_INTERACTIONS_KEY
            ),
        )
    }

    private fun buildIssueReportCallbackExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "customer-issue-report-callback")
        }

    @ExperimentalBitdriftApi
    private fun logPreviousRunInfoToBitdrift() {
        Capture.Logger.getPreviousRunInfo()?.let { previousRunInfo ->
            val terminationReason = previousRunInfo.terminationReason?.toString() ?: ""
            val fields = mapOf(
                "hasFatallyTerminated" to previousRunInfo.hasFatallyTerminated.toString(),
                "terminationReason" to terminationReason,
            )
            Capture.Logger.logInfo(fields) {
                "Capture.Logger.getPreviousRunInfo"
            }
        }
    }

    private fun addSessionUrlToThirdPartySdks(applicationContext: Context, sessionUrl: String) {
        if (Sentry.isEnabled()) {
            Sentry.setExtra(bitdriftSessionUrlKey, sessionUrl)
        }

        if (Bugsnag.isStarted()) {
            val frontendTabName = "bitdrift_session_url"
            Bugsnag.addMetadata(frontendTabName, bitdriftSessionUrlKey, sessionUrl)
        }

        FirebaseApp.getApps(applicationContext)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?.let {
                FirebaseCrashlytics.getInstance()
                    .setCustomKey(bitdriftSessionUrlKey, sessionUrl)
            }
    }

    private class CustomerIssueReportCallback : IssueReportCallback {
        override fun onBeforeReportSend(report: Report) {
            Capture.Logger.logInfo(
                mapOf(
                    "reportType" to report.reportType,
                    "session" to report.sessionId,
                    "details" to report.details,
                    "reason" to report.reason,
                    "fields" to report.fields.toString(),
                )
            ) {
                "IssueReportCallback.onBeforeReportSend"
            }
        }
    }

    private sealed class PersistedSdkConfigResult {
        data class Failed(
            val message: String,
        ) : PersistedSdkConfigResult()

        data class Success(
            val captureSdkInitSettings: CaptureSdkInitSettings,
        ) : PersistedSdkConfigResult()
    }

    private data class CaptureSdkInitSettings(
        val apiUrl: HttpUrl,
        val apiKey: String,
        val sessionStrategy: SessionStrategy,
        val configuration: Configuration,
        val fieldProviders: List<FieldProvider>,
    )
}
