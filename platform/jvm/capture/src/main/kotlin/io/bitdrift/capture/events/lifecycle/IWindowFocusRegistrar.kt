// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.Activity

/**
 * Observes window focus for an [Activity], decoupling the focus mechanism from the reaction to it
 * so the latter can be unit tested by driving focus changes directly.
 */
internal interface IWindowFocusRegistrar {
    /**
     * Starts observing focus for [activity]. Must be idempotent per activity — lifecycle callbacks
     * can repeat.
     */
    fun register(
        activity: Activity,
        onFocusChanged: (hasFocus: Boolean) -> Unit,
    )

    /** Stops observing focus for [activity]. Safe to call for an activity that was never registered. */
    fun unregister(activity: Activity)

    /** Stops observing focus for every registered activity. */
    fun unregisterAll()
}
