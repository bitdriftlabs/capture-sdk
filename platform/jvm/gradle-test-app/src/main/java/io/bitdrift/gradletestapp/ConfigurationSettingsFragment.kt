// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.bitdrift.capture.providers.session.SessionStrategy
import kotlin.system.exitProcess

class ConfigurationSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        val backendCategory = PreferenceCategory(context)
        backendCategory.key = "control_plane_category"
        backendCategory.title = "Control Plane Configuration"

        screen.addPreference(backendCategory)

        val apiUrlPref = EditTextPreference(context)
        apiUrlPref.key = "apiUrl"
        apiUrlPref.title = "API URL"
        apiUrlPref.summary = SELECTION_SUMMARY

        backendCategory.addPreference(apiUrlPref)

        val apiKeyPref = EditTextPreference(context)
        apiKeyPref.key = "apiKey"
        apiKeyPref.title = "API Key"
        apiKeyPref.summary = SELECTION_SUMMARY

        backendCategory.addPreference(apiKeyPref)

        val restartPreference = Preference(context)
        restartPreference.key = "restart"
        restartPreference.title = "Restart the App"
        restartPreference.setOnPreferenceClickListener {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val restartIntent = Intent.makeRestartActivityTask(launchIntent!!.component)
            // Required for API 34 and later
            // Ref: https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
            restartIntent.setPackage(context.packageName);
            context.startActivity(restartIntent)
            exitProcess(0)
        }

        val sessionStrategyPref = buildSessionStrategyOption(context)
        backendCategory.addPreference(sessionStrategyPref)

        screen.addPreference(restartPreference)

        preferenceScreen = screen
    }

    private fun buildSessionStrategyOption(context: Context): ListPreference {
        val sessionStrategyPref = ListPreference(context)
        sessionStrategyPref.key = SESSION_STRATEGY_PREFS_KEY
        sessionStrategyPref.title = SESSION_STRATEGY_TITLE
        sessionStrategyPref.entries = SESSION_STRATEGY_ENTRIES
        sessionStrategyPref.entryValues = SESSION_STRATEGY_ENTRIES
        sessionStrategyPref.summary = SELECTION_SUMMARY
        sessionStrategyPref.setDefaultValue(SessionStrategyPreferences.FIXED.displayName)
        return sessionStrategyPref
    }

    enum class SessionStrategyPreferences(val displayName: String) {
        FIXED("Fixed"),
        ACTIVITY_BASED("Activity Based")
    }

    companion object {
        const val SESSION_STRATEGY_PREFS_KEY = "sessionStrategy"
        private const val SELECTION_SUMMARY = "App needs to be restarted for changes to take effect"
        private const val SESSION_STRATEGY_TITLE = "Session Strategy"
        private val SESSION_STRATEGY_ENTRIES =
            arrayOf(SessionStrategyPreferences.FIXED.displayName, SessionStrategyPreferences.ACTIVITY_BASED.displayName)
    }
}
