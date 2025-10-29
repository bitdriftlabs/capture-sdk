// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.Logger.sessionUrl
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.timber.CaptureTree
import io.bitdrift.gradletestapp.BuildConfig
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.BITDRIFT_API_KEY
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.util.UUID

/**
 * Starts bitdrift's Captures SDK with the persisted config settings
 */
object BitdriftInit {
    /**
     * Init sdk with the persisted settings
     */
    fun init(
        applicationContext: Context,
        sharedPreferences: SharedPreferences,
    ): Boolean {
        val apiKey =
            sharedPreferences.getString(BITDRIFT_API_KEY, null)
        val apiUrl = sharedPreferences.getString("apiUrl", null)
        if (apiKey.isNullOrBlank() || apiUrl.isNullOrBlank()) {
            Timber.w("SDK initialization skipped - API key or URL not configured. Please set them in Settings.")
            return false
        }

        if (initFromPreferences(sharedPreferences, applicationContext)) {
            if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
            Timber.plant(CaptureTree())
            Timber.i("Bitdrift Logger initialized with session_url=$sessionUrl")
            return true
        } else {
            Timber.e("Failed to initialize Bitdrift SDK - check your API key and URL configuration")
            return false
        }
    }

    private fun initFromPreferences(
        sharedPreferences: SharedPreferences,
        context: Context,
    ): Boolean {
        val settingsResult = getPersistedCaptureSdkSettings(sharedPreferences)
        when (settingsResult) {
            is SdkConfigResult.Success -> {
                val settings = settingsResult.captureSdkInitSettings
                Capture.Logger.start(
                    apiKey = settings.apiKey,
                    apiUrl = settings.apiUrl,
                    configuration = settings.configuration,
                    sessionStrategy = settings.sessionStrategy,
                    fieldProviders = settings.fieldProviders,
                    context = context,
                )
                Log.i("GradleTestApp", "Session initialized " + Capture.Logger.sessionUrl)
                return true
            }

            is SdkConfigResult.Failed -> {
                Log.e("GradleTestApp", settingsResult.message)
                return false
            }
        }
    }

    private fun getPersistedCaptureSdkSettings(sharedPreferences: SharedPreferences): SdkConfigResult {
        val apiKey =
            sharedPreferences.getString(
                BITDRIFT_API_KEY,
                null,
            )
        val apiUrl = sharedPreferences.getString("apiUrl", null)?.toHttpUrlOrNull()
        if (apiUrl == null || apiKey == null) {
            return SdkConfigResult.Failed("Invalid settings. apiUrl: $apiUrl . apiKey: $apiKey")
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

        val sessionStrategy = getSessionStrategy(sharedPreferences)
        val configuration =
            Configuration(
                sessionReplayConfiguration = if (sessionReplayEnabled) SessionReplayConfiguration() else null,
                enableFatalIssueReporting = fatalIssueReporterEnabled,
                enableNativeCrashReporting = fatalIssueReporterEnabled,
            )

        val userID = UUID.randomUUID().toString()
        val fieldProviders =
            listOf(
                FieldProvider { mapOf("user_id" to userID) },
            )
        val captureSdkInitSettings =
            CaptureSdkInitSettings(
                apiUrl = apiUrl,
                apiKey = apiKey,
                sessionStrategy = sessionStrategy,
                configuration = configuration,
                fieldProviders = fieldProviders,
            )
        return SdkConfigResult.Success(captureSdkInitSettings)
    }

    private fun getSessionStrategy(sharedPreferences: SharedPreferences): SessionStrategy =
        if (sharedPreferences.getString(
                ConfigurationSettingsFragment.Companion.SESSION_STRATEGY_PREFS_KEY,
                ConfigurationSettingsFragment.SessionStrategyPreferences.FIXED.displayName,
            ) == "Fixed"
        ) {
            SessionStrategy.Fixed()
        } else {
            SessionStrategy.ActivityBased(
                inactivityThresholdMins = 60L,
                onSessionIdChanged = { sessionId ->
                    Timber.Forest.i("Bitdrift Logger session id updated: $sessionId")
                },
            )
        }

    private sealed class SdkConfigResult {
        data class Failed(
            val message: String,
        ) : SdkConfigResult()

        data class Success(
            val captureSdkInitSettings: CaptureSdkInitSettings,
        ) : SdkConfigResult()
    }

    private data class CaptureSdkInitSettings(
        val apiUrl: HttpUrl,
        val apiKey: String,
        val sessionStrategy: SessionStrategy,
        val configuration: Configuration,
        val fieldProviders: List<FieldProvider>,
    )
}
