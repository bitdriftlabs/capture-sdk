// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
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
        apiUrlPref.summary = "App needs to be restarted for changes to take effect"

        backendCategory.addPreference(apiUrlPref)

        val apiKeyPref = EditTextPreference(context)
        apiKeyPref.key = "apiKey"
        apiKeyPref.title = "API Key"
        apiKeyPref.summary = "App needs to be restarted for changes to take effect"

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

        screen.addPreference(restartPreference)

        preferenceScreen = screen
    }
}
