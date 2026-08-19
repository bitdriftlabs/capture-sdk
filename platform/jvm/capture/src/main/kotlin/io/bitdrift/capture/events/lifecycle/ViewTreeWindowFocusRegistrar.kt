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
 * [IWindowFocusRegistrar] backed by [ViewTreeObserver.OnWindowFocusChangeListener].
 *
 * `Application.ActivityLifecycleCallbacks` has no focus callback and `Activity.onWindowFocusChanged`
 * requires subclassing, so neither is usable from inside an SDK. The window's `ViewTreeObserver` is,
 * and it has been available since API 18 — comfortably below this SDK's minSdk — so no version gating
 * is needed.
 *
 * The listener bookkeeping here is modelled on square/papa's `ViewTreeObservers`: hold the wrapper so
 * it can actually be removed later, and check [ViewTreeObserver.isAlive] on both add and remove,
 * because a dead observer throws on mutation. Papa reaches for `curtains` to find windows; this SDK
 * already discovers activities via `ActivityLifecycleCallbacks`, so that dependency is not needed.
 */
internal class ViewTreeWindowFocusRegistrar : IWindowFocusRegistrar {
    /**
     * Weak keys so a leaked or forgotten activity cannot be retained by this map. The listener is the
     * value because [ViewTreeObserver.removeOnWindowFocusChangeListener] needs the same instance that
     * was added — keeping it is the only way to unregister rather than leak.
     */
    private val listeners = WeakHashMap<Activity, ViewTreeObserver.OnWindowFocusChangeListener>()

    @UiThread
    override fun register(
        activity: Activity,
        onFocusChanged: (Boolean) -> Unit,
    ) {
        // Activity callbacks can repeat for the same instance; registering twice would double every
        // subsequent flush.
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
        // peekDecorView avoids forcing a new decor view into existence purely to tear one down, and
        // the observer is commonly already dead by the time an activity is destroyed.
        val observer = activity.window?.peekDecorView()?.viewTreeObserver ?: return
        if (observer.isAlive) {
            observer.removeOnWindowFocusChangeListener(listener)
        }
    }
}
