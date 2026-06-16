// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.bitdrift.gradletestapp.diagnostics.fatalissues.ThirdPartyCrashReportersInitializer
import io.bitdrift.gradletestapp.diagnostics.lifecycle.ActivitySpanCallbacks
import io.bitdrift.gradletestapp.diagnostics.papa.PapaTelemetry
import io.bitdrift.gradletestapp.diagnostics.startup.AppStartInfoLogger
import io.bitdrift.gradletestapp.diagnostics.strictmode.StrictModeConfigurator
import io.bitdrift.gradletestapp.init.CaptureSdkInitializer
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.DEFERRED_START_PREFS_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.DIAGNOSTICS_ENABLED_KEY
import timber.log.Timber

/**
 * A Kotlin app entry point that initializes the Bitdrift Logger automatically only when ConfigState.isDeferredStart is set to false
 */
class GradleTestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        ThirdPartyCrashReportersInitializer.init(this)

        if (areAdditionalDiagnosticToolsEnabled(sharedPreferences)) {
            attachDiagnosticTools()
        }

        if (hasDeferredSdkStartConfigured(sharedPreferences)) {
            Timber.i("Deferred start enabled - SDK initialization skipped")
        } else {
            CaptureSdkInitializer.initFromPreferences(this, sharedPreferences)
        }
    }

    private fun areAdditionalDiagnosticToolsEnabled(sharedPreferences: SharedPreferences): Boolean =
        sharedPreferences.getBoolean(DIAGNOSTICS_ENABLED_KEY, false)

    private fun hasDeferredSdkStartConfigured(sharedPreferences: SharedPreferences): Boolean =
        sharedPreferences.getBoolean(DEFERRED_START_PREFS_KEY, false)

    private fun attachDiagnosticTools() {
        StrictModeConfigurator.install()
        ActivitySpanCallbacks.create()
        AppStartInfoLogger.register(this)
        PapaTelemetry.install()
    }
}
