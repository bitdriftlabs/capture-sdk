// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.CRASH_PROFILING_ENABLED_PREFS_KEY
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CrashProfilingSettingsTest {
    private lateinit var controller: ActivityController<FragmentActivity>

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 35)
    }

    @Test
    fun rejectsEnablingBelowApi36WithoutPersistingOptIn() {
        val preference = preference(35)

        preference.click()

        assertFalse(preference.isChecked)
        assertFalse(persistedEnabled())
        assertEquals(controller.get().getString(R.string.crash_profiling_unsupported), ShadowToast.getTextOfLatestToast())
        assertEquals(controller.get().getString(R.string.crash_profiling_unsupported), preference.summary)
    }

    @Test
    fun api36EnablesOnlyAnrAndKeepsTheLimitationVisible() {
        val preference = preference(36)

        preference.click()

        assertTrue(preference.isChecked)
        assertTrue(persistedEnabled())
        val notice = controller.get().getString(R.string.crash_profiling_anr_only)
        assertEquals(notice, ShadowToast.getTextOfLatestToast())
        assertEquals(notice, preference.summary)

        preference.click()
        assertFalse(persistedEnabled())
        assertEquals("Disabled", preference.summary)
    }

    @Test
    fun api37EnablesBothKindsWithoutAWarning() {
        val preference = preference(37)

        preference.click()

        assertTrue(preference.isChecked)
        assertTrue(persistedEnabled())
        assertEquals("Enabled", preference.summary)
        assertEquals(0, ShadowToast.shownToastCount())

        preference.click()
        assertFalse(persistedEnabled())
    }

    // Exercise AndroidX's real toggle, listener, and persistence path together.
    @SuppressLint("RestrictedApi")
    private fun SwitchPreference.click() {
        performClick()
    }

    private fun preference(apiLevel: Int): SwitchPreference {
        controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.Theme_MyApplication)
        controller.setup()
        // Exercise the version gates without requiring newer platform implementations to render preferences.
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", apiLevel)
        val fragment = ConfigurationSettingsFragment()
        controller
            .get()
            .supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        return checkNotNull(fragment.findPreference(CRASH_PROFILING_ENABLED_PREFS_KEY))
    }

    private fun persistedEnabled(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(controller.get()).getBoolean(CRASH_PROFILING_ENABLED_PREFS_KEY, false)
}
