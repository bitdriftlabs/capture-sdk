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
        val observer = activity.window?.decorView?.viewTreeObserver ?: return
        if (!observer.isAlive) {
            return
        }
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> onFocusChanged(hasFocus) }
        observer.addOnWindowFocusChangeListener(listener)
        listeners[activity] = listener
    }

    @UiThread
    override fun unregister(activity: Activity) {
        val listener = listeners.remove(activity) ?: return
        removeListener(activity, listener)
    }

    @UiThread
    override fun unregisterAll() {
        // Snapshot first: WeakHashMap iteration is not safe against structural changes.
        val snapshot = listeners.entries.map { it.key to it.value }
        listeners.clear()
        snapshot.forEach { (activity, listener) -> removeListener(activity, listener) }
    }

    private fun removeListener(
        activity: Activity,
        listener: ViewTreeObserver.OnWindowFocusChangeListener,
    ) {
        // peekDecorView: don't create a decor view just to tear one down.
        val observer = activity.window?.peekDecorView()?.viewTreeObserver ?: return
        if (observer.isAlive) {
            observer.removeOnWindowFocusChangeListener(listener)
        }
    }
}
