// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import io.bitdrift.gradletestapp.ui.compose.components.SettingsApiKeysDialogFragment
import kotlin.system.exitProcess

class ConfigurationSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        val backendCategory = PreferenceCategory(context)
        backendCategory.key = "control_plane_category"

        screen.addPreference(backendCategory)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultApiUrl = "https://api.bitdrift.io"

        // Set default value if not already set
        if (!sharedPreferences.contains("apiUrl")) {
            sharedPreferences.edit { putString("apiUrl", defaultApiUrl) }
        }

        val apiUrlPref = EditTextPreference(context)
        apiUrlPref.key = BITDRIFT_URL_KEY
        apiUrlPref.title = "API URL"
        val currentUrl = sharedPreferences.getString(BITDRIFT_URL_KEY, "") ?: ""
        apiUrlPref.summary = if (currentUrl.isBlank()) "Enter API URL" else currentUrl
        apiUrlPref.setOnBindEditTextListener { edit ->
            edit.hint = defaultApiUrl
        }
        apiUrlPref.setOnPreferenceChangeListener { _, newValue ->
            val apiUrl = newValue as? String ?: ""
            val isValid = apiUrl.isNotBlank() && (apiUrl.startsWith("https://") || apiUrl.startsWith("http://"))
            apiUrlPref.summary = if (isValid) "Valid API URL" else "Invalid API URL (must start with http:// or https://)"
            true
        }

        backendCategory.addPreference(apiUrlPref)

        val apiKeyPref = EditTextPreference(context)
        apiKeyPref.key = "api_key"
        apiKeyPref.title = "bitdrift's API Key"
        val currentKey = sharedPreferences.getString(BITDRIFT_API_KEY, "") ?: ""
        apiKeyPref.summary = if (currentKey.isBlank()) "Enter your bitdrift API key" else "API key set"
        apiKeyPref.setOnPreferenceChangeListener { _, newValue ->
            val apiKey = newValue as? String ?: ""
            val isValid = apiKey.isNotBlank() && apiKey.length >= 10
            apiKeyPref.summary = if (isValid) "Valid API key" else "Invalid API key (must be at least 10 characters)"
            true
        }

        backendCategory.addPreference(apiKeyPref)

        val apiKeysPreference = Preference(context)
        apiKeysPreference.key = "api_keys"
        apiKeysPreference.title = "Other API Keys"
        apiKeysPreference.summary = SELECTION_SUMMARY
        apiKeysPreference.setOnPreferenceClickListener {
            showApiKeysDialog(context)
            true
        }
        backendCategory.addPreference(apiKeysPreference)

        val restartPreference = Preference(context)
        restartPreference.key = "restart"
        restartPreference.title = "Restart the App"
        restartPreference.setOnPreferenceClickListener {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val restartIntent = Intent.makeRestartActivityTask(launchIntent!!.component)
            // Required for API 34 and later
            // Ref: https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
            restartIntent.setPackage(context.packageName)
            context.startActivity(restartIntent)
            exitProcess(0)
        }

        backendCategory.addPreference(buildSessionStrategyList(context))
        backendCategory.addPreference(buildSwitchPreference(context))
        backendCategory.addPreference(buildDeferredStartSwitch(context))

        screen.addPreference(restartPreference)

        preferenceScreen = screen
    }

    private fun buildSessionStrategyList(context: Context): ListPreference {
        val listPreference = ListPreference(context)
        listPreference.key = SESSION_STRATEGY_PREFS_KEY
        listPreference.title = SESSION_STRATEGY_TITLE
        listPreference.entries = SESSION_STRATEGY_ENTRIES
        listPreference.entryValues = SESSION_STRATEGY_ENTRIES
        listPreference.summary = SELECTION_SUMMARY
        listPreference.setDefaultValue(SessionStrategyPreferences.FIXED.displayName)
        return listPreference
    }

    private fun buildSwitchPreference(context: Context): SwitchPreference {
        val switchPreference = SwitchPreference(context)
        switchPreference.key = FATAL_ISSUE_ENABLED_PREFS_KEY
        switchPreference.title = FATAL_ISSUE_TITLE
        switchPreference.summary = SELECTION_SUMMARY
        switchPreference.setDefaultValue(true)
        switchPreference.setOnPreferenceChangeListener { _, newValue ->
            val summaryText = if (newValue == true) "Enabled" else "Disabled"
            switchPreference.summary = "$summaryText - $SELECTION_SUMMARY"
            true
        }
        return switchPreference
    }

    private fun buildDeferredStartSwitch(context: Context): SwitchPreference {
        val switchPreference = SwitchPreference(context)
        switchPreference.key = DEFERRED_START_PREFS_KEY
        switchPreference.title = DEFERRED_START_TITLE
        switchPreference.summary = SELECTION_SUMMARY
        switchPreference.setDefaultValue(false)
        switchPreference.setOnPreferenceChangeListener { _, newValue ->
            val summaryText = if (newValue == true) "Enabled" else "Disabled"
            switchPreference.summary = "$summaryText - $SELECTION_SUMMARY"
            true
        }
        return switchPreference
    }

    private fun showApiKeysDialog(context: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        SettingsApiKeysDialogFragment(sharedPreferences).show(parentFragmentManager, "")
    }

    enum class SessionStrategyPreferences(
        val displayName: String,
    ) {
        FIXED("Fixed"),
        ACTIVITY_BASED("Activity Based"),
    }

    companion object {
        const val BITDRIFT_API_KEY = "api_key"

        const val BITDRIFT_URL_KEY = "apiUrl"

        const val SESSION_STRATEGY_PREFS_KEY = "sessionStrategy"
        const val FATAL_ISSUE_ENABLED_PREFS_KEY = "fatalIssueEnabled"
        const val DEFERRED_START_PREFS_KEY = "deferredStart"
        private const val SELECTION_SUMMARY = "App needs to be restarted for changes to take effect"
        private const val SESSION_STRATEGY_TITLE = "Session Strategy"
        private const val FATAL_ISSUE_TITLE = "Fatal Issue Reporter"
        private const val DEFERRED_START_TITLE = "Deferred SDK Start"

        private val SESSION_STRATEGY_ENTRIES =
            arrayOf(
                SessionStrategyPreferences.FIXED.displayName,
                SessionStrategyPreferences.ACTIVITY_BASED.displayName,
            )
    }
}
