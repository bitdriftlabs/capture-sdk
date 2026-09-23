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
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.reports.IssueCallbackConfiguration
import io.bitdrift.capture.reports.IssueReportCallback
import io.bitdrift.capture.reports.Report
import io.bitdrift.capture.timber.CaptureTree
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.BITDRIFT_API_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.DEFAULT_SIMULATED_START_DELAY_MILLIS
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.SIMULATED_START_DELAY_MILLIS_PREFS_KEY
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts bitdrift's Captures SDK with the persisted config settings
 */
object CaptureSdkInitializer {
    private val userUuid = UUID.randomUUID().toString()
    private val bitdriftSessionUrlKey = "bitdrift_session_url"
    private val backgroundStartScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isCaptureTreePlanted = AtomicBoolean(false)
    private val _sdkInitializationState = MutableStateFlow<Boolean?>(null)
    private val _isStarting = MutableStateFlow(false)

    val sdkInitializationState: StateFlow<Boolean?> = _sdkInitializationState.asStateFlow()
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    val currentUserUuid: String
        get() = userUuid

    /**
     * Init sdk with the persisted settings
     */
    @OptIn(ExperimentalBitdriftApi::class)
    fun initFromPreferences(
        applicationContext: Context,
        sharedPreferences: SharedPreferences,
    ): Boolean {
        if (Capture.Logger.sessionUrl != null) {
            _sdkInitializationState.value = true
            return true
        }

        _sdkInitializationState.value = null

        val persistedSdkConfigResult = getPersistedCaptureSdkSettings(
            applicationContext,
            sharedPreferences,
        )

        return when (persistedSdkConfigResult) {

            is PersistedSdkConfigResult.Success -> {
                plantCaptureTree()
                _isStarting.value = true

                val startAction = {
                    startCaptureSdk(persistedSdkConfigResult.captureSdkInitSettings, applicationContext)
                    logPreviousRunInfoToBitdrift()
                }

                if (shouldStartOnBackgroundThread(sharedPreferences)) {
                    backgroundStartScope.launch { startAction() }
                } else {
                    startAction()
                }

                true
            }

            is PersistedSdkConfigResult.Failed -> {
                Timber.i(persistedSdkConfigResult.message)
                _isStarting.value = false
                _sdkInitializationState.value = false
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
        val onStartResult: (CaptureResult<ILogger>) -> Unit = { startResult ->
            when (startResult) {
                is CaptureResult.Success -> {
                    val logger = startResult.value
                    Log.d("bitdrift","SDK started successfully. sessionId=${logger.sessionId}, sessionUrl=${logger.sessionUrl}, userUuid=${userUuid}")
                    Capture.Logger.setEntityId(userUuid)
                    addSessionUrlToThirdPartySdks(context, logger.sessionUrl)
                    _isStarting.value = false
                    _sdkInitializationState.value = true
                }

                is CaptureResult.Failure -> {
                    Log.d("bitdrift","SDK failed to start: ${startResult.error.message}")
                    _isStarting.value = false
                    _sdkInitializationState.value = false
                    // Re-throwing on debug builds so we can get immediate signal of
                    // any issues at Capture.Logger.start internals during the development phase.
                    throw IllegalStateException(startResult.error.message)
                }
            }
        }

        if (settings.simulateStartDelay) {
            startCaptureSdkWithSimulatedDelay(
                apiKey = settings.apiKey,
                apiUrl = settings.apiUrl,
                configuration = settings.configuration,
                sessionStrategy = settings.sessionStrategy,
                initialFields = settings.initialFields,
                context = context,
                startResult = onStartResult,
                delayMillis = settings.simulatedStartDelayMillis,
            )
        } else {
            Capture.Logger.start(
                apiKey = settings.apiKey,
                apiUrl = settings.apiUrl,
                configuration = settings.configuration,
                sessionStrategy = settings.sessionStrategy,
                initialFields = settings.initialFields,
                context = context,
                startResult = onStartResult,
            )
        }
    }

    private fun shouldStartOnBackgroundThread(sharedPreferences: SharedPreferences): Boolean =
        sharedPreferences.getBoolean(
            ConfigurationSettingsFragment.Companion.START_ON_BACKGROUND_THREAD_PREFS_KEY,
            true,
        )

    private fun plantCaptureTree() {
        if (isCaptureTreePlanted.compareAndSet(false, true)) {
            Timber.plant(CaptureTree())
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
            )
        val initialFields = mapOf("user_id" to userUuid)

        val simulateStartDelay =
            sharedPreferences.getBoolean(
                ConfigurationSettingsFragment.Companion.SIMULATED_START_DELAY_PREFS_KEY,
                false
            )

        val simulatedStartDelayMillis =
            sharedPreferences.getString(SIMULATED_START_DELAY_MILLIS_PREFS_KEY, null)
                ?.toLongOrNull()
                ?.takeIf { it >= 0 }
                ?: DEFAULT_SIMULATED_START_DELAY_MILLIS

        val captureSdkInitSettings =
            CaptureSdkInitSettings(
                apiUrl = apiUrl,
                apiKey = apiKey,
                sessionStrategy = sessionStrategy,
                configuration = configuration,
                initialFields = initialFields,
                simulateStartDelay = simulateStartDelay,
                simulatedStartDelayMillis = simulatedStartDelayMillis,
            )
        return PersistedSdkConfigResult.Success(captureSdkInitSettings)
    }

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
        val initialFields: Map<String, String>,
        val simulateStartDelay: Boolean,
        val simulatedStartDelayMillis: Long,
    )
}
