// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import io.bitdrift.capture.reports.FatalIssueMechanism
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
        backendCategory.title = "Control Plane Configuration"

        screen.addPreference(backendCategory)

        val apiUrlPref = EditTextPreference(context)
        apiUrlPref.key = "apiUrl"
        apiUrlPref.title = "API URL"
        apiUrlPref.summary = SELECTION_SUMMARY

        backendCategory.addPreference(apiUrlPref)

        val apiKeysPreference = Preference(context)
        apiKeysPreference.key = "api_keys"
        apiKeysPreference.title = "API Keys"
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

        val sessionStrategyPref =
            buildListPreference(
                SESSION_STRATEGY_PREFS_KEY,
                SESSION_STRATEGY_TITLE,
                SESSION_STRATEGY_ENTRIES,
                SessionStrategyPreferences.FIXED.displayName,
                context,
            )
        val fatalIssueReportingSource =
            buildListPreference(
                FATAL_ISSUE_SOURCE_PREFS_KEY,
                FATAL_ISSUE_TYPE_TITLE,
                FATAL_ISSUE_REPORTING_TYPES,
                FatalIssueMechanism.BuiltIn.displayName,
                context,
            )
        backendCategory.addPreference(sessionStrategyPref)
        backendCategory.addPreference(fatalIssueReportingSource)

        screen.addPreference(restartPreference)

        preferenceScreen = screen
    }

    private fun buildListPreference(
        keyName: String,
        title: String,
        entries: Array<String>,
        defaultValue: String,
        context: Context,
    ): ListPreference {
        val listPreference = ListPreference(context)
        listPreference.key = keyName
        listPreference.title = title
        listPreference.entries = entries
        listPreference.entryValues = entries
        listPreference.summary = SELECTION_SUMMARY
        listPreference.setDefaultValue(defaultValue)
        return listPreference
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
        const val SESSION_STRATEGY_PREFS_KEY = "sessionStrategy"
        const val FATAL_ISSUE_SOURCE_PREFS_KEY = "fatalIssueSource"
        private const val SELECTION_SUMMARY = "App needs to be restarted for changes to take effect"
        private const val SESSION_STRATEGY_TITLE = "Session Strategy"
        private const val FATAL_ISSUE_TYPE_TITLE = "Fatal Issue Mechanism"
        private val FATAL_ISSUE_REPORTING_TYPES =
            arrayOf(
                FatalIssueMechanism.BuiltIn.displayName,
                "NONE",
            )

        private val SESSION_STRATEGY_ENTRIES =
            arrayOf(SessionStrategyPreferences.FIXED.displayName, SessionStrategyPreferences.ACTIVITY_BASED.displayName)

        fun getFatalIssueSourceConfig(sharedPreferences: SharedPreferences): String =
            sharedPreferences.getString(FATAL_ISSUE_SOURCE_PREFS_KEY, null) ?: ""
    }
}
