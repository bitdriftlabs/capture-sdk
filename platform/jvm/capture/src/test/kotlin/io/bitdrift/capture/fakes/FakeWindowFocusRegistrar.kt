// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import android.app.Activity
import io.bitdrift.capture.events.lifecycle.IWindowFocusRegistrar

/**
 * [IWindowFocusRegistrar] fake that lets a test drive window-focus changes directly and observe
 * exactly which activities currently have a focus observer.
 *
 * One behaviour is deliberately mirrored from the real `ViewTreeObserver`: a callback that was
 * registered and never unregistered keeps receiving focus changes — including after the component
 * that registered it has "stopped". That is what makes missing-teardown bugs observable here.
 */
internal class FakeWindowFocusRegistrar : IWindowFocusRegistrar {
    private val callbacks = LinkedHashMap<Activity, (hasFocus: Boolean) -> Unit>()

    /** The activities that currently have a focus observer registered. */
    val registeredActivities: Set<Activity>
        get() = callbacks.keys

    override fun register(
        activity: Activity,
        onFocusChanged: (hasFocus: Boolean) -> Unit,
    ) {
        // Mirrors the real registrar's contract: repeat registration must not duplicate observers.
        if (callbacks.containsKey(activity)) {
            return
        }
        callbacks[activity] = onFocusChanged
    }

    override fun unregister(activity: Activity) {
        callbacks.remove(activity)
    }

    // Not part of IWindowFocusRegistrar (yet): the production teardown that would need it does not
    // exist either. When stop()-time teardown lands in the interface this becomes an `override`.
    fun unregisterAll() {
        callbacks.clear()
    }

    /** Simulates the platform dispatching a window-focus change for [activity]'s window. */
    fun changeFocus(
        activity: Activity,
        hasFocus: Boolean,
    ) {
        callbacks[activity]?.invoke(hasFocus)
    }
}
