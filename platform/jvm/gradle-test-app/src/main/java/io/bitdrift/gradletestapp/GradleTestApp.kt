// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import io.bitdrift.capture.Capture
import io.bitdrift.gradletestapp.diagnostics.fatalissues.ThirdPartyCrashReportersInitializer
import io.bitdrift.gradletestapp.diagnostics.lifecycle.ActivitySpanCallbacks
import io.bitdrift.gradletestapp.diagnostics.papa.PapaTelemetry
import io.bitdrift.gradletestapp.diagnostics.startup.AppStartInfoLogger
import io.bitdrift.gradletestapp.diagnostics.strictmode.StrictModeConfigurator
import io.bitdrift.gradletestapp.init.CaptureSdkInitializer
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.DEFERRED_START_PREFS_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.DIAGNOSTICS_ENABLED_KEY
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * A Kotlin app entry point that initializes the Bitdrift Logger automatically only when ConfigState.isDeferredStart is set to false
 */
class GradleTestApp : Application() {
    override fun onCreate() {
        val duration = measureTime {
            super.onCreate()

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

            if (areAdditionalDiagnosticToolsEnabled(sharedPreferences)) {
                attachDiagnosticTools()
            }

            ThirdPartyCrashReportersInitializer.init(this)

            if (hasDeferredSdkStartConfigured(sharedPreferences)) {
                Timber.i("Deferred start enabled - SDK initialization skipped")
            } else {
                CaptureSdkInitializer.initFromPreferences(this, sharedPreferences)
            }

        }
        val onStartDurationMessage =
            "onCreate duration is ${duration.inWholeMilliseconds} ms"
        Log.d("bitdrit gradle-test-app", onStartDurationMessage)
        Capture.Logger.logInfo { onStartDurationMessage }
    }

    private fun areAdditionalDiagnosticToolsEnabled(sharedPreferences: SharedPreferences): Boolean =
        sharedPreferences.getBoolean(DIAGNOSTICS_ENABLED_KEY, false)

    private fun hasDeferredSdkStartConfigured(sharedPreferences: SharedPreferences): Boolean =
        sharedPreferences.getBoolean(DEFERRED_START_PREFS_KEY, false)

    private fun attachDiagnosticTools() {
        ActivitySpanCallbacks.create()
        AppStartInfoLogger.register(this)
        PapaTelemetry.install()
    }
}
