// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.ui.compose.components.SettingsApiKeysDialogFragment
import kotlin.system.exitProcess

class ConfigurationSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        addRestartCategory(context, screen)
        addInitialConfigurationCategory(context, screen, sharedPreferences)
        addConfigurationOptionsCategory(context, screen)

        preferenceScreen = screen
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.bitdrift_background))
        setDivider(ColorDrawable(ContextCompat.getColor(view.context, R.color.bitdrift_border)))
        setDividerHeight(1)
        listView.clipToPadding = false
        listView.setPadding(0, 0, 0, LIST_BOTTOM_PADDING_PX)
    }

    /** Attaches the category to [screen] up front: adding children before that throws. */
    private fun newCategory(
        context: Context,
        screen: PreferenceScreen,
        key: String,
        title: String?,
    ): PreferenceCategory {
        val category =
            PreferenceCategory(context).apply {
                this.key = key
                this.title = title
                isIconSpaceReserved = false
            }
        screen.addPreference(category)
        return category
    }

    private fun addRestartCategory(
        context: Context,
        screen: PreferenceScreen,
    ) {
        val category = newCategory(context, screen, "restart_category", null)

        val restartPreference = Preference(context)
        restartPreference.key = "restart"
        restartPreference.title = context.getString(R.string.restart_warning_title)
        restartPreference.summary = context.getString(R.string.restart_warning_summary)
        restartPreference.icon = ContextCompat.getDrawable(context, R.drawable.ic_warning_24)
        restartPreference.setOnPreferenceClickListener {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val restartIntent = Intent.makeRestartActivityTask(launchIntent!!.component)
            // Required for API 34 and later
            // Ref: https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
            restartIntent.setPackage(context.packageName)
            context.startActivity(restartIntent)
            exitProcess(0)
        }
        category.addPreference(restartPreference)
    }

    private fun addInitialConfigurationCategory(
        context: Context,
        screen: PreferenceScreen,
        sharedPreferences: SharedPreferences,
    ) {
        val category = newCategory(context, screen, "control_plane_category", "Initial configuration")

        val defaultApiUrl = "https://api.bitdrift.io"

        // Set default value if not already set
        if (!sharedPreferences.contains(BITDRIFT_URL_KEY)) {
            sharedPreferences.edit { putString(BITDRIFT_URL_KEY, defaultApiUrl) }
        }

        val apiUrlPref = EditTextPreference(context)
        apiUrlPref.key = BITDRIFT_URL_KEY
        apiUrlPref.title = "API URL"
        apiUrlPref.isIconSpaceReserved = false
        val currentUrl = sharedPreferences.getString(BITDRIFT_URL_KEY, "") ?: ""
        apiUrlPref.summary = currentUrl.ifBlank { "Enter API URL" }
        apiUrlPref.setOnBindEditTextListener { edit ->
            edit.hint = defaultApiUrl
        }
        apiUrlPref.setOnPreferenceChangeListener { _, newValue ->
            val apiUrl = newValue as? String ?: ""
            val isValid = apiUrl.isNotBlank() && apiUrl.startsWith("https://")
            apiUrlPref.summary = if (isValid) "Valid API URL" else "Invalid API URL (must start with https://)"
            true
        }
        category.addPreference(apiUrlPref)

        val apiKeyPref = EditTextPreference(context)
        apiKeyPref.key = BITDRIFT_API_KEY
        apiKeyPref.title = "bitdrift's API Key"
        apiKeyPref.isIconSpaceReserved = false
        val currentKey = sharedPreferences.getString(BITDRIFT_API_KEY, "") ?: ""
        apiKeyPref.summary = if (currentKey.isBlank()) "Enter your bitdrift API key" else "API key set"
        apiKeyPref.setOnPreferenceChangeListener { _, newValue ->
            val apiKey = newValue as? String ?: ""
            val isValid = apiKey.isNotBlank() && apiKey.length >= 10
            apiKeyPref.summary = if (isValid) "Valid API key" else "Invalid API key (must be at least 10 characters)"
            true
        }
        category.addPreference(apiKeyPref)

        val apiKeysPreference = Preference(context)
        apiKeysPreference.key = "api_keys"
        apiKeysPreference.title = "Other API Keys"
        apiKeysPreference.summary = "Manage the keys used by the other backends"
        apiKeysPreference.isIconSpaceReserved = false
        apiKeysPreference.setOnPreferenceClickListener {
            showApiKeysDialog(context)
            true
        }
        category.addPreference(apiKeysPreference)
        category.addPreference(buildSessionStrategyList(context))
        category.addPreference(buildInactivityThresholdPreference(context))
        category.addPreference(buildDeferredStartSwitch(context))
    }

    private fun addConfigurationOptionsCategory(
        context: Context,
        screen: PreferenceScreen,
    ) {
        val category = newCategory(context, screen, "capture_category", "Configuration Options")
        category.addPreference(buildSwitchPreference(context))
        category.addPreference(buildSessionReplaySwitch(context))
        category.addPreference(buildDiagnosticsSwitch(context))
    }

    private fun enabledSummary(enabled: Boolean): String = if (enabled) "Enabled" else "Disabled"

    private fun buildSessionStrategyList(context: Context): ListPreference {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val listPreference = ListPreference(context)
        listPreference.key = SESSION_STRATEGY_PREFS_KEY
        listPreference.isIconSpaceReserved = false
        listPreference.title = SESSION_STRATEGY_TITLE
        listPreference.entries = SESSION_STRATEGY_ENTRIES
        listPreference.entryValues = SESSION_STRATEGY_ENTRIES
        listPreference.setDefaultValue(SessionStrategyPreferences.FIXED.displayName)
        listPreference.summary = sharedPreferences.getString(
            SESSION_STRATEGY_PREFS_KEY,
            SessionStrategyPreferences.FIXED.displayName,
        )
        listPreference.setOnPreferenceChangeListener { _, newValue ->
            val isActivityBased = newValue == SessionStrategyPreferences.ACTIVITY_BASED.displayName
            findPreference<EditTextPreference>(INACTIVITY_THRESHOLD_PREFS_KEY)?.isVisible = isActivityBased
            listPreference.summary = newValue as String
            true
        }
        return listPreference
    }

    private fun buildInactivityThresholdPreference(context: Context): EditTextPreference {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editTextPreference = EditTextPreference(context)
        editTextPreference.key = INACTIVITY_THRESHOLD_PREFS_KEY
        editTextPreference.isIconSpaceReserved = false
        editTextPreference.title = "Inactivity Threshold (minutes)"
        editTextPreference.setDefaultValue(DEFAULT_INACTIVITY_THRESHOLD_MINS.toString())
        val currentValue = sharedPreferences.getString(INACTIVITY_THRESHOLD_PREFS_KEY, DEFAULT_INACTIVITY_THRESHOLD_MINS.toString())
        editTextPreference.summary = "$currentValue minutes"
        editTextPreference.setOnBindEditTextListener { edit ->
            edit.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            edit.hint = DEFAULT_INACTIVITY_THRESHOLD_MINS.toString()
        }
        editTextPreference.setOnPreferenceChangeListener { _, newValue ->
            val mins = newValue.toString().toLongOrNull() ?: DEFAULT_INACTIVITY_THRESHOLD_MINS
            editTextPreference.summary = "$mins minutes"
            true
        }
        val currentStrategy = sharedPreferences.getString(SESSION_STRATEGY_PREFS_KEY, SessionStrategyPreferences.FIXED.displayName)
        editTextPreference.isVisible = currentStrategy == SessionStrategyPreferences.ACTIVITY_BASED.displayName
        return editTextPreference
    }

    private fun buildSwitchPreference(
        context: Context,
        key: String,
        title: String,
        defaultValue: Boolean,
    ): SwitchPreference {
        val switchPreference = SwitchPreference(context)
        switchPreference.key = key
        switchPreference.title = title
        switchPreference.isIconSpaceReserved = false
        switchPreference.setDefaultValue(defaultValue)
        switchPreference.summary =
            enabledSummary(
                PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, defaultValue),
            )
        switchPreference.setOnPreferenceChangeListener { _, newValue ->
            switchPreference.summary = enabledSummary(newValue == true)
            true
        }
        return switchPreference
    }

    private fun buildSwitchPreference(context: Context): SwitchPreference =
        buildSwitchPreference(context, FATAL_ISSUE_ENABLED_PREFS_KEY, FATAL_ISSUE_TITLE, true)

    private fun buildDeferredStartSwitch(context: Context): SwitchPreference =
        buildSwitchPreference(context, DEFERRED_START_PREFS_KEY, DEFERRED_START_TITLE, false)

    private fun buildSessionReplaySwitch(context: Context): SwitchPreference =
        buildSwitchPreference(context, SESSION_REPLAY_ENABLED_PREFS_KEY, SESSION_REPLAY_TITLE, true)

    private fun buildDiagnosticsSwitch(context: Context): SwitchPreference =
        buildSwitchPreference(context, DIAGNOSTICS_ENABLED_KEY, DIAGNOSTICS_TITLE, true)

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
        const val SESSION_REPLAY_ENABLED_PREFS_KEY = "sessionReplayEnabled"
        const val DIAGNOSTICS_ENABLED_KEY = "diagnosticsEnabled"
        const val WEBVIEW_MONITORING_PREFS_KEY = "webviewMonitoring"

        const val INACTIVITY_THRESHOLD_PREFS_KEY = "inactivityThresholdMins"
        const val PREFS_SLEEP_MODE_ENABLED = "sleep_mode_enabled"

        private const val DEFAULT_INACTIVITY_THRESHOLD_MINS = 30L
        private const val LIST_BOTTOM_PADDING_PX = 48
        private const val SESSION_STRATEGY_TITLE = "Session Strategy"
        private const val FATAL_ISSUE_TITLE = "Fatal Issue Reporter"
        private const val DEFERRED_START_TITLE = "Deferred SDK Start"
        private const val SESSION_REPLAY_TITLE = "Session Replay"
        private const val DIAGNOSTICS_TITLE = "Diagnostics Tools"
        private const val WEBVIEW_MONITORING_TITLE = "WebView Monitoring"

        private val SESSION_STRATEGY_ENTRIES =
            arrayOf(
                SessionStrategyPreferences.FIXED.displayName,
                SessionStrategyPreferences.ACTIVITY_BASED.displayName,
            )
    }
}
