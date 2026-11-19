// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.lifecycle

import android.app.Activity
import android.util.Log
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Emits a minimal, stable set of lifecycle breadcrumbs to logcat so automated harnesses can
 * correlate app state transitions with SDK behaviour — for example, deciding whether a stats flush
 * was triggered by backgrounding or by an internal timer.
 *
 * Only two things are logged, both at INFO:
 *  - **process** lifecycle, from [ProcessLifecycleOwner]
 *  - **window focus**, forwarded by the hosting activity
 *
 * Per-activity lifecycle is deliberately omitted. The platform already writes it to the logcat
 * `events` buffer (`wm_on_stop_called`, `wm_on_paused_called`, `wm_stop_activity`, …), so repeating
 * it here would only add noise. Process lifecycle is the part the platform does *not* expose, and
 * it is what the SDK's background flush actually hangs off — `ProcessLifecycleOwner` debounces stop
 * events by roughly 700ms, so it fires meaningfully later than the activity's own `onStop`.
 *
 * Window focus is included because it is the earliest signal that the app is leaving the
 * foreground, ahead of both the activity stop and the process stop.
 *
 * Message formats are kept fixed so they stay cheap to parse:
 * ```
 * I bitdrift-lifecycle: process ON_STOP
 * I bitdrift-lifecycle: window MainActivity focus=false
 * ```
 */
object LifecycleEventLogger {
    const val TAG = "bitdrift-lifecycle"

    /**
     * Registers the process-lifecycle observer. Call from `Application.onCreate`, which runs on the
     * main thread as [ProcessLifecycleOwner] requires.
     */
    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                Log.i(TAG, "process $event")
            },
        )
    }

    /**
     * Forwards `Activity.onWindowFocusChanged`, which has no process-level equivalent and so cannot
     * be observed from [install] alone.
     */
    fun onWindowFocusChanged(
        activity: Activity,
        hasFocus: Boolean,
    ) {
        Log.i(TAG, "window ${activity.javaClass.simpleName} focus=$hasFocus")
    }
}
