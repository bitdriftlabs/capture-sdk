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
 * Exercises [ViewTreeWindowFocusRegistrar] against a real Robolectric [Activity] and its
 * [android.view.ViewTreeObserver].
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
        // dispatchOnWindowFocusChange is what ViewRootImpl calls on a focus change, but it is hidden
        // from the SDK stubs; the real class runs under Robolectric, so reflection reaches it.
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
        registrar.register(activity) { hasFocus -> receivedFocusChanges.add(hasFocus) }
        registrar.unregister(activity)
        registrar.register(activity) { hasFocus -> receivedFocusChanges.add(hasFocus) }

        dispatchFocusChange(false)

        assertThat(receivedFocusChanges).containsExactly(false)
    }
}
