// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.fatalissues

import android.app.Application
import androidx.preference.PreferenceManager
import com.bugsnag.android.Bugsnag
import io.bitdrift.gradletestapp.ui.compose.components.SettingsApiKeysDialogFragment.Companion.BUG_SNAG_SDK_API_KEY
import io.bitdrift.gradletestapp.ui.compose.components.SettingsApiKeysDialogFragment.Companion.SENTRY_SDK_DSN_KEY
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import timber.log.Timber
import kotlin.time.measureTime

object CrashSdkInitializer {
    fun init(application: Application) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        val bugSnagApiKey = sharedPreferences.getString(BUG_SNAG_SDK_API_KEY, "")
        if (!bugSnagApiKey.isNullOrEmpty()) {
            val bugSnagStartTime =
                measureTime {
                    Bugsnag.start(application, bugSnagApiKey)
                }
            Timber.i("Bugsnag.start() took ${bugSnagStartTime.inWholeMilliseconds} ms")
        }

        val sentryKey = sharedPreferences.getString(SENTRY_SDK_DSN_KEY, "")
        if (!sentryKey.isNullOrEmpty()) {
            val sentryStartTime =
                measureTime {
                    SentryAndroid.init(application) { options: SentryAndroidOptions ->
                        options.dsn = sentryKey
                        options.isEnabled = true
                    }
                }
            Timber.i("SentryAndroid.init() took ${sentryStartTime.inWholeMilliseconds} ms")
        }
    }
}
