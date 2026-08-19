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
 * [IWindowFocusRegistrar] fake that lets tests drive focus changes directly. Like a real
 * `ViewTreeObserver`, a never-unregistered callback keeps receiving changes.
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
        if (callbacks.containsKey(activity)) {
            return
        }
        callbacks[activity] = onFocusChanged
    }

    override fun unregister(activity: Activity) {
        callbacks.remove(activity)
    }

    override fun unregisterAll() {
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
