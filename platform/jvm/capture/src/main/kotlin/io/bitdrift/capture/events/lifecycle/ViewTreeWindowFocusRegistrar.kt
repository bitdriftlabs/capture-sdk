// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.Activity
import android.view.ViewTreeObserver
import androidx.annotation.UiThread
import java.util.WeakHashMap

/**
 * [IWindowFocusRegistrar] backed by [ViewTreeObserver.OnWindowFocusChangeListener] (API 18) — the
 * only focus signal observable from an SDK without subclassing activities. Bookkeeping follows
 * square/papa's `ViewTreeObservers`: keep the listener instance so it can be removed, and check
 * [ViewTreeObserver.isAlive] before mutating, since a dead observer throws.
 */
internal class ViewTreeWindowFocusRegistrar : IWindowFocusRegistrar {
    // Weak keys so the map never retains an activity.
    private val listeners = WeakHashMap<Activity, ViewTreeObserver.OnWindowFocusChangeListener>()

    @UiThread
    override fun register(
        activity: Activity,
        onFocusChanged: (Boolean) -> Unit,
    ) {
        // Registering twice would double every flush.
        if (listeners.containsKey(activity)) {
            return
        }
        val observer =
            activity.window
                ?.decorView
                ?.viewTreeObserver
                ?.takeIf { it.isAlive } ?: return
        val listener = ViewTreeObserver.OnWindowFocusChangeListener(onFocusChanged)
        observer.addOnWindowFocusChangeListener(listener)
        listeners[activity] = listener
    }

    @UiThread
    override fun unregister(activity: Activity) {
        val listener = listeners.remove(activity) ?: return
        // peekDecorView: don't create a decor view just to tear one down.
        activity.window
            ?.peekDecorView()
            ?.viewTreeObserver
            ?.takeIf { it.isAlive }
            ?.removeOnWindowFocusChangeListener(listener)
    }

    @UiThread
    override fun unregisterAll() {
        // toList snapshots the keys: unregister mutates the map, and WeakHashMap iterators are not
        // structurally safe.
        listeners.keys.toList().forEach(::unregister)
    }
}
