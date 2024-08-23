// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.Logger.sessionUrl
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.timber.CaptureTree
import timber.log.Timber
import kotlin.random.Random

/**
 * A Java app entry point that initializes the Bitdrift Logger.
 */
class GradleTestApp : Application() {

    private var activitySpan: Span? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("Hello World!")
        initLogging()
        trackAppLifecycle()
    }

    private fun initLogging() {
        BitdriftInit.initBitdriftCaptureInJava()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(CaptureTree())
        Timber.i("Bitdrift Logger initialized with session_url=%s", sessionUrl)
    }

    private fun trackAppLifecycle(){
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
                activitySpan = Capture.Logger.startSpan(
                    name = "Activity in Foreground",
                    level = LogLevel.INFO,
                    fields = mapOf("activity_name" to activity.localClassName))
            }

            override fun onActivityPaused(activity: Activity) {
                if (Random.nextBoolean()) {
                    activitySpan?.end(SpanResult.SUCCESS)
                } else {
                    activitySpan?.end(SpanResult.FAILURE)
                }
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        })
    }
}
