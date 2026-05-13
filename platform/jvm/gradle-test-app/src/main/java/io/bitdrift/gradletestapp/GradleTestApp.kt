// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.Application
import io.bitdrift.capture.Capture.Logger.sessionUrl
import io.bitdrift.capture.timber.CaptureTree
import io.bitdrift.gradletestapp.diagnostics.fatalissues.CrashSdkInitializer
import io.bitdrift.gradletestapp.diagnostics.lifecycle.ActivitySpanCallbacks
import io.bitdrift.gradletestapp.diagnostics.papa.PapaTelemetry
import io.bitdrift.gradletestapp.diagnostics.startup.AppStartInfoLogger
import io.bitdrift.gradletestapp.diagnostics.strictmode.StrictModeConfigurator
import timber.log.Timber

/**
 * App entry point. Capture SDK is auto-initialized via CaptureInitializer (manifest meta-data).
 */
class GradleTestApp : Application() {
    private val shouldTriggerCrash = false
    override fun onCreate() {
        if (shouldTriggerCrash) {
            throw IllegalStateException("Crash before Application super.onCreate()")
        }
        super.onCreate()
        Timber.plant(CaptureTree())
        Timber.i("Bitdrift auto-initialized with session_url=$sessionUrl")
        attachAdditionalMonitoringTools()
    }

    private fun attachAdditionalMonitoringTools() {
        StrictModeConfigurator.install()
        CrashSdkInitializer.init(this)
        ActivitySpanCallbacks.create()
        AppStartInfoLogger.register(this)
        PapaTelemetry.install()
    }
}
