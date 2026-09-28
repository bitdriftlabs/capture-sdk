// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.fatalissues

import android.app.Activity
import android.util.Log
import io.bitdrift.gradletestapp.BuildConfig
import io.bitdrift.gradletestapp.data.model.AppExitReason
import io.bitdrift.gradletestapp.data.repository.AppExitRepository
import io.bitdrift.gradletestapp.init.CaptureSdkInitializer
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Debug-only entry point that forces the [AppExitReason] passed via [APP_EXIT_REASON_EXTRA] once
 * the SDK has started, so CI can verify the resulting fatal issue report on the next launch.
 */
internal object AppExitIntentHandler {
    const val LOG_TAG = "BitdriftE2E"
    const val APP_EXIT_REASON_EXTRA = "e2e_app_exit_reason"

    private val SDK_START_TIMEOUT = 30.seconds

    fun handle(activity: Activity) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val intent = activity.intent ?: return
        val reasonName = intent.getStringExtra(APP_EXIT_REASON_EXTRA) ?: return
        intent.removeExtra(APP_EXIT_REASON_EXTRA)

        val reason = AppExitReason.entries.firstOrNull { it.name == reasonName }
        if (reason == null) {
            Log.e(LOG_TAG, "Unknown app exit reason: $reasonName")
            return
        }

        val applicationContext = activity.applicationContext
        Thread({
            if (!awaitSdkStarted()) {
                Log.e(LOG_TAG, "SDK did not start, skipping app exit reason=${reason.name}")
                return@Thread
            }
            Log.i(LOG_TAG, "Triggering app exit reason=${reason.name}")
            AppExitRepository().triggerAppExit(applicationContext, reason)
        }, "app-exit-intent-trigger").start()
    }

    private fun awaitSdkStarted(): Boolean =
        runBlocking {
            withTimeoutOrNull(SDK_START_TIMEOUT) {
                CaptureSdkInitializer.sdkInitializationState.filterNotNull().first()
            } == true
        }
}
