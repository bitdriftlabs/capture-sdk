// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.Activity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises [ViewTreeWindowFocusRegistrar] against a real Robolectric [Activity], so a real decor
 * view and a real `ViewTreeObserver` are in play. Focus changes are driven through
 * `ViewTreeObserver.dispatchOnWindowFocusChange`, the same entry point the framework uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ViewTreeWindowFocusRegistrarTest {
    private val registrar = ViewTreeWindowFocusRegistrar()
    private val receivedFocusChanges = mutableListOf<Boolean>()

    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun dispatchFocusChange(hasFocus: Boolean) {
        // dispatchOnWindowFocusChange is what ViewRootImpl calls on a real window focus change, but
        // it is package-private and hidden from the SDK stubs — under Robolectric the real framework
        // class runs, so reflection reaches it.
        val observer = activity.window.decorView.viewTreeObserver
        observer.javaClass
            .getDeclaredMethod("dispatchOnWindowFocusChange", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(observer, hasFocus)
    }

    @Test
    fun deliversFocusChangesForARegisteredActivity() {
        registrar.register(activity) { hasFocus -> receivedFocusChanges.add(hasFocus) }

        dispatchFocusChange(false)
        dispatchFocusChange(true)

        assertThat(receivedFocusChanges).containsExactly(false, true)
    }

    @Test
    fun repeatRegistrationDoesNotDuplicateDelivery() {
        // Activity callbacks legitimately repeat (stop/start cycles); a duplicate observer would
        // double every subsequent flush.
        registrar.register(activity) { hasFocus -> receivedFocusChanges.add(hasFocus) }
        registrar.register(activity) { hasFocus -> receivedFocusChanges.add(hasFocus) }

        dispatchFocusChange(false)

        assertThat(receivedFocusChanges).containsExactly(false)
    }

    @Test
    fun unregisterStopsDelivery() {
        registrar.register(activity) { hasFocus -> receivedFocusChanges.add(hasFocus) }

        registrar.unregister(activity)
        dispatchFocusChange(false)

        assertThat(receivedFocusChanges).isEmpty()
    }

    @Test
    fun unregisterForANeverRegisteredActivityIsANoOp() {
        registrar.unregister(activity)

        dispatchFocusChange(false)

        assertThat(receivedFocusChanges).isEmpty()
    }

    @Test
    fun registrationSurvivesAStopStartCycle() {
        // The flush logger unregisters on stop and re-registers on start; the registrar must come
        // back to a working state on the same activity instance.
        registrar.register(activity) { hasFocus -> receivedFocusChanges.add(hasFocus) }
        registrar.unregister(activity)
        registrar.register(activity) { hasFocus -> receivedFocusChanges.add(hasFocus) }

        dispatchFocusChange(false)

        assertThat(receivedFocusChanges).containsExactly(false)
    }
}
