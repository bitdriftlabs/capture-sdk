// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.helloworld

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.session.SessionStrategy
import okhttp3.HttpUrl
import java.util.UUID
import io.bitdrift.capture.CaptureResult

private const val bitdriftAPIKey = "<YOUR API KEY GOES HERE>"
private val BITDRIFT_URL = HttpUrl.Builder().scheme("https").host("api.bitdrift.io").build()
private val kLoggerRunningDurationThreshold = 15_000L

class HelloWorldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        setupExampleCrashHandler()

        val userID = UUID.randomUUID().toString();

        Logger.startAsync(
            apiKey = bitdriftAPIKey,
            apiUrl = BITDRIFT_URL,
            sessionStrategy = SessionStrategy.Fixed { UUID.randomUUID().toString() },
            configuration = Configuration(enableFatalIssueReporting = true),
            fieldProviders = listOf(
                FieldProvider {
                    mapOf(
                        "foo" to "bar",
                        "user_id" to userID
                    )
                }
            )
        ){ result ->
                when (result) {
                    is CaptureResult.Success -> {
                        Log.i(
                            "HelloWorldApp",
                            "Android Bitdrift app launched with session url=${result.value.sessionUrl}"
                        )
                    }
                    is CaptureResult.Failure -> {
                        Log.i(
                            "HelloWorldApp",
                            "Android Bitdrift failed to start due to error=${result.error.message}"
                        )
                    }
                }
            }

        Handler(Looper.getMainLooper()).postDelayed({
            Log.i("HelloWorldApp", "Capture Logger has been running for $kLoggerRunningDurationThreshold ms")
        }, kLoggerRunningDurationThreshold)
    }

    private fun setupExampleCrashHandler() {
        val ogHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.w("HelloWorldApp", "Uncaught exception in thread ${thread.name}", throwable)
            ogHandler?.uncaughtException(thread, throwable)
        }
    }
}
