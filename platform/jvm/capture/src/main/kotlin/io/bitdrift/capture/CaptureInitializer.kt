// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.startup.Initializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Auto-initializes Capture SDK by reading configuration from AndroidManifest <meta-data> tags.
 *
 * Required meta-data:
 *   io.bitdrift.capture.API_KEY - The API key for Capture.
 *
 * Optional meta-data:
 *   io.bitdrift.capture.API_URL               - Custom API URL (default: https://api.bitdrift.io).
 *   io.bitdrift.capture.SESSION_STRATEGY      - "fixed" or "activity_based" (default: activity_based).
 *   io.bitdrift.capture.INACTIVITY_THRESHOLD_IN_MINUTES - Minutes for activity_based strategy (default: 30).
 *   io.bitdrift.capture.ENABLE_SESSION_REPLAY - "true"/"false" (default: true).
 *   io.bitdrift.capture.ENABLE_FATAL_ISSUE_REPORTING - "true"/"false" (default: true).
 *   io.bitdrift.capture.SLEEP_MODE            - "enabled"/"disabled" (default: disabled).
 */
class CaptureInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val appContext = context.applicationContext
        val metadata =
            appContext.packageManager
                .getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)
                .metaData
        if (metadata == null) {
            Log.w(LOG_TAG, "No metadata found in manifest, skipping auto-init")
            return
        }

        val apiKey = metadata.getString(KEY_API_KEY)
        if (apiKey.isNullOrBlank()) {
            Log.w(LOG_TAG, "Missing $KEY_API_KEY in manifest, skipping auto-init")
            return
        }

        val apiUrl = metadata.getString(KEY_API_URL)?.let { parseUrl(it) }
        val sessionStrategy = buildSessionStrategy(metadata)
        val configuration = buildConfiguration(metadata)

        Capture.Logger.start(
            apiKey = apiKey,
            sessionStrategy = sessionStrategy,
            configuration = configuration,
            apiUrl = apiUrl ?: defaultUrl(),
            context = appContext,
        )
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(ContextHolder::class.java)

    private fun buildSessionStrategy(metadata: android.os.Bundle): io.bitdrift.capture.providers.session.SessionStrategy {
        val strategy = metadata.getString(KEY_SESSION_STRATEGY) ?: "activity_based"
        return when (strategy.lowercase()) {
            "fixed" ->
                io.bitdrift.capture.providers.session.SessionStrategy
                    .Fixed()
            else -> {
                val threshold = metadata.getInt(KEY_INACTIVITY_THRESHOLD_IN_MINUTES, 30).toLong()
                io.bitdrift.capture.providers.session.SessionStrategy.ActivityBased(
                    inactivityThresholdMins = threshold,
                )
            }
        }
    }

    private fun buildConfiguration(metadata: android.os.Bundle): Configuration {
        val enableReplay = metadata.getBoolean(KEY_ENABLE_SESSION_REPLAY, true)
        val enableFatalIssue = metadata.getBoolean(KEY_ENABLE_FATAL_ISSUE_REPORTING, true)
        val sleepMode =
            when ((metadata.getString(KEY_SLEEP_MODE) ?: "disabled").lowercase()) {
                "enabled" -> SleepMode.ENABLED
                else -> SleepMode.DISABLED
            }
        return Configuration(
            sessionReplayConfiguration =
                if (enableReplay) {
                    io.bitdrift.capture.replay
                        .SessionReplayConfiguration()
                } else {
                    null
                },
            enableFatalIssueReporting = enableFatalIssue,
            sleepMode = sleepMode,
        )
    }

    private fun parseUrl(url: String): HttpUrl? =
        try {
            url.toHttpUrl()
        } catch (e: IllegalArgumentException) {
            Log.w(LOG_TAG, "Invalid API URL: $url", e)
            null
        }

    private fun defaultUrl() =
        HttpUrl
            .Builder()
            .scheme("https")
            .host("api.bitdrift.io")
            .build()

    /**
     * TODO: Just prototyping for now...
     */
    companion object {
        private const val LOG_TAG = "CaptureInit"
        private const val KEY_API_KEY = "io.bitdrift.capture.API_KEY"
        private const val KEY_API_URL = "io.bitdrift.capture.API_URL"
        private const val KEY_SESSION_STRATEGY = "io.bitdrift.capture.SESSION_STRATEGY"
        private const val KEY_INACTIVITY_THRESHOLD_IN_MINUTES = "io.bitdrift.capture.INACTIVITY_THRESHOLD_IN_MINUTES"
        private const val KEY_ENABLE_SESSION_REPLAY = "io.bitdrift.capture.ENABLE_SESSION_REPLAY"
        private const val KEY_ENABLE_FATAL_ISSUE_REPORTING =
            "io.bitdrift.capture.ENABLE_FATAL_ISSUE_REPORTING"
        private const val KEY_SLEEP_MODE = "io.bitdrift.capture.SLEEP_MODE"
    }
}
