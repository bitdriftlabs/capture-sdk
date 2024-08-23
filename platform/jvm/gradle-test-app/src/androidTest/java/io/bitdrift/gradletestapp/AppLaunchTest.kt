// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Test

class AppLaunchTest {
    @Test
    fun appLaunchesSuccessfully() {
        // Launch the activity using ActivityScenario
        launchActivity<MainActivity>().use {
            // Dumb matcher logic to check if a view is displayed, i.e. if the app launched successfully
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        }
    }
}
