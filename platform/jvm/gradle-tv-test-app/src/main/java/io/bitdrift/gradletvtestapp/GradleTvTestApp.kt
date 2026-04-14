// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletvtestapp

import android.app.Application
import android.util.Log
import io.bitdrift.capture.Capture
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class GradleTvTestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startCaptureIfConfigured()
    }

    fun startCaptureIfConfigured(): Boolean {
        if (Capture.logger() != null) {
            return true
        }

        val settings = TvSettingsStore.load(this)
        val apiKey = settings.apiKey.trim()
        val apiUrl = settings.apiUrl.trim().toHttpUrlOrNull()
        if (apiKey.isEmpty() || apiUrl == null) {
            Log.w(LOG_TAG, "Capture not started. Configure API key and URL in Settings.")
            return false
        }

        Capture.Logger.start(
            apiKey = apiKey,
            apiUrl = apiUrl,
            sessionStrategy = SessionStrategy.ActivityBased(inactivityThresholdMins = 30L),
            fieldProviders = listOf(FieldProvider { mapOf("app_surface" to "android_tv") }),
            context = this,
        )

        Capture.Logger.logInfo(
            mapOf(
                "version" to BuildConfig.VERSION_NAME,
                "surface" to "android_tv",
            )
        ) {
            "Android TV sample started"
        }

        return true
    }

    companion object {
        private const val LOG_TAG = "GradleTvTestApp"
    }
}
