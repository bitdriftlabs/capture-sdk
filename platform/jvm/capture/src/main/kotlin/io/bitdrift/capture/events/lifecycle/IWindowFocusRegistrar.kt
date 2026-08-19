// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.Activity

/**
 * Observes window focus for an [Activity].
 *
 * Exists as an interface for two reasons. It keeps the *mechanism* of observing focus separate from
 * the *reaction* to losing it, so the reacting logger can be unit tested by driving focus changes
 * directly instead of standing up a real `Window` and `ViewTreeObserver`. And it leaves room for a
 * different mechanism later without touching the caller.
 */
internal interface IWindowFocusRegistrar {
    /**
     * Starts observing focus for [activity], invoking [onFocusChanged] with the new focus state.
     *
     * Implementations must tolerate being called more than once for the same [activity] without
     * registering duplicate observers, because activity callbacks can legitimately repeat.
     */
    fun register(
        activity: Activity,
        onFocusChanged: (hasFocus: Boolean) -> Unit,
    )

    /**
     * Stops observing focus for [activity]. Must be safe to call for an activity that was never
     * registered, or whose window has already been torn down.
     */
    fun unregister(activity: Activity)

    /**
     * Stops observing focus for every activity currently registered. Needed when the owning event
     * listener stops: once its lifecycle callbacks are unregistered, per-activity unregistration can
     * never happen again, so anything still registered would keep firing.
     */
    fun unregisterAll()
}
