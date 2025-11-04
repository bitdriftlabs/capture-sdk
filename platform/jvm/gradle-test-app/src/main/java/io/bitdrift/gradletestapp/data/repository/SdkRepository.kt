// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.SleepMode
import io.bitdrift.gradletestapp.init.BitdriftInit
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.BITDRIFT_API_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.BITDRIFT_URL_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.DEFERRED_START_PREFS_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.PREFS_SLEEP_MODE_ENABLED
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.SESSION_REPLAY_ENABLED_PREFS_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Repository that manages SDK state and operations
 */
class SdkRepository(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(applicationContext)

    suspend fun initializeSdk(
        apiKey: String,
        apiUrl: String,
    ): Boolean {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(BITDRIFT_API_KEY, apiKey)
                putString(BITDRIFT_URL_KEY, apiUrl)
            }
        }
        return withContext(Dispatchers.Main.immediate) {
            BitdriftInit.init(applicationContext, sharedPreferences)
        }
    }

    suspend fun startNewSession(): String? =
        withContext(Dispatchers.IO) {
            try {
                Logger.startNewSession()
                Logger.sessionId
            } catch (e: Exception) {
                Timber.e(e, "Failed to start new session")
                null
            }
        }

    suspend fun generateDeviceCode(): Result<String> =
        suspendCancellableCoroutine { continuation ->
            Logger.createTemporaryDeviceCode { captureResult ->
                when (captureResult) {
                    is CaptureResult.Success -> continuation.resume(Result.success(captureResult.value))
                    is CaptureResult.Failure -> {
                        val error = "Failed to generate device code: ${captureResult.error.message}"
                        Timber.e(error)
                        continuation.resume(Result.failure(Exception(error)))
                    }
                }
            }
        }

    suspend fun getConfig(): SdkConfig =
        SdkConfig(
            apiKey = getApiKey(),
            apiUrl = getApiUrl(),
            sessionStrategy = getSessionStrategy(),
            isDeferredStart = isDeferredStartEnabled(),
            isSessionReplayEnabled = isSessionReplayEnabled(),
            isSleepModeEnabled = isSleepModeEnabled(),
        )

    /**
     * Get current session URL (reads in-memory value; no I/O)
     */
    fun getSessionUrl(): String? = Logger.sessionUrl

    /**
     * Get current session ID (reads in-memory value; no I/O)
     */
    fun getSessionId(): String? = Logger.sessionId

    /**
     * Check if SDK is initialized (combines in-memory state with cached prefs)
     */
    fun isSdkInitialized(): Boolean {
        val apiKey = sharedPreferences.getString(BITDRIFT_API_KEY, "") ?: ""
        val apiUrl = sharedPreferences.getString("apiUrl", "") ?: ""
        return Logger.sessionUrl != null &&
            apiKey.isNotBlank() &&
            apiUrl.isNotBlank() &&
            validateApiKey(apiKey) &&
            validateApiUrl(apiUrl)
    }

    /**
     * Check if deferred start is enabled
     */
    suspend fun isDeferredStartEnabled(): Boolean =
        withContext(Dispatchers.IO) {
            sharedPreferences.getBoolean(
                DEFERRED_START_PREFS_KEY,
                false,
            )
        }

    suspend fun isSessionReplayEnabled(): Boolean =
        withContext(Dispatchers.IO) {
            sharedPreferences.getBoolean(
                SESSION_REPLAY_ENABLED_PREFS_KEY,
                true,
            )
        }

    suspend fun isSleepModeEnabled(): Boolean =
        withContext(Dispatchers.IO) {
            sharedPreferences.getBoolean(
                PREFS_SLEEP_MODE_ENABLED,
                false,
            )
        }

    /**
     * Get current session strategy
     */
    suspend fun getSessionStrategy(): String =
        withContext(Dispatchers.IO) {
            val strategy = sharedPreferences.getString("sessionStrategy", "Fixed") ?: "Fixed"
            when (strategy) {
                "Fixed" -> "Fixed"
                "Activity Based" -> "Activity Based"
                else -> "Fixed"
            }
        }

    /**
     * Save API key to preferences
     */
    suspend fun saveApiKey(apiKey: String) = persistKeyValue(BITDRIFT_API_KEY, apiKey)

    /**
     * Save API URL to preferences
     */
    suspend fun saveApiUrl(apiUrl: String) = persistKeyValue(BITDRIFT_URL_KEY, apiUrl)

    suspend fun getApiKey(): String = getPersistedValue(BITDRIFT_API_KEY)

    suspend fun getApiUrl(): String = getPersistedValue(BITDRIFT_URL_KEY)

    fun logMessage(
        logLevel: LogLevel,
        message: String,
    ) {
        when (logLevel) {
            LogLevel.TRACE -> Timber.v(message)
            LogLevel.DEBUG -> Timber.d(message)
            LogLevel.INFO -> Timber.i(message)
            LogLevel.WARNING -> Timber.w(message)
            LogLevel.ERROR -> Timber.e(message)
        }
    }

    fun setSleepModeEnabled(enabled: Boolean) {
        val mode = if (enabled) SleepMode.ENABLED else SleepMode.DISABLED
        Logger.setSleepMode(mode)
        sharedPreferences.edit { putBoolean(PREFS_SLEEP_MODE_ENABLED, enabled) }
    }

    /**
     * Validate API key format
     */
    private fun validateApiKey(apiKey: String): Boolean = apiKey.isNotBlank() && apiKey.length >= 10

    /**
     * Validate API URL format
     */
    private fun validateApiUrl(apiUrl: String): Boolean =
        apiUrl.isNotBlank() && (apiUrl.startsWith("https://") || apiUrl.startsWith("http://"))

    private suspend fun getPersistedValue(key: String): String = withContext(Dispatchers.IO) { sharedPreferences.getString(key, "") ?: "" }

    private suspend fun persistKeyValue(
        key: String,
        value: String,
    ) {
        withContext(Dispatchers.IO) {
            sharedPreferences
                .edit {
                    putString(key, value)
                }
        }
    }

    data class SdkConfig(
        val apiKey: String,
        val apiUrl: String,
        val sessionStrategy: String,
        val isDeferredStart: Boolean,
        val isSessionReplayEnabled: Boolean,
        val isSleepModeEnabled: Boolean,
    )
}
