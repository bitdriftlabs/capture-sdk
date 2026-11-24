// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.annotation.SuppressLint
import android.app.Application
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import io.bitdrift.capture.Capture
import io.bitdrift.capture.ILogger
import io.bitdrift.gradletestapp.data.EntityIdSettings
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment
import io.bitdrift.gradletestapp.ui.fragments.EntityIdDialogFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EntityIdSettingsTest {
    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var fragment: ConfigurationSettingsFragment
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.Theme_MyApplication)
        controller.setup()
        preferences = PreferenceManager.getDefaultSharedPreferences(controller.get())
        openSettings()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun startsUnsetAndClearsAnyPreviouslyRegisteredSdkId() {
        assertEquals(notSetSummary(), row().summary)
        assertFalse(preferences.contains(EntityIdSettings.PREFS_KEY))

        val logger = mock(ILogger::class.java)
        EntityIdSettings.applySaved(preferences, logger)

        verify(logger).clearEntityId()
        verifyNoMoreInteractions(logger)
    }

    @Test
    fun savesBeforeStartupAndAppliesLatestValueWhenSdkStarts() {
        openEditor().submit("first-test-id")
        openEditor().submit("latest-test-id")
        openSettings()
        assertEquals("latest-test-id", row().summary)
        assertEquals("latest-test-id", preferences.getString(EntityIdSettings.PREFS_KEY, null))

        val logger = mock(ILogger::class.java)
        EntityIdSettings.applySaved(preferences, logger)

        verify(logger).setEntityId("latest-test-id")
        verifyNoMoreInteractions(logger)
    }

    @Test
    fun editsApplyLiveCancelDoesNothingAndClearPersists() {
        val logger = mock(ILogger::class.java)
        mockStatic(Capture::class.java).use { capture ->
            capture.`when`<ILogger> { Capture.logger() }.thenReturn(logger)
            openEditor().submit("live-test-id")
            verify(logger).setEntityId("live-test-id")
            assertEquals("live-test-id", row().summary)

            val editor = openEditor()
            val input = checkNotNull(editor.findViewById<EditText>(android.R.id.edit))
            assertEquals("live-test-id", input.text.toString())
            input.append("-cancelled")
            editor.clickButton(DialogInterface.BUTTON_NEGATIVE)
            assertEquals("live-test-id", preferences.getString(EntityIdSettings.PREFS_KEY, null))
            verifyNoMoreInteractions(logger)

            openEditor().clickButton(DialogInterface.BUTTON_NEUTRAL)
            verify(logger).clearEntityId()
            verifyNoMoreInteractions(logger)
            assertFalse(preferences.contains(EntityIdSettings.PREFS_KEY))
            assertEquals(notSetSummary(), row().summary)
        }

        openSettings()
        assertEquals(notSetSummary(), row().summary)
    }

    private fun openSettings() {
        fragment = ConfigurationSettingsFragment()
        controller.get().supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
    }

    private fun row(): Preference = checkNotNull(fragment.findPreference(EntityIdSettings.PREFS_KEY))

    private fun notSetSummary(): String = controller.get().getString(R.string.entity_id_not_set)

    @SuppressLint("RestrictedApi")
    private fun openEditor(): AlertDialog {
        row().performClick()
        val manager = fragment.childFragmentManager
        manager.executePendingTransactions()
        val dialog = manager.findFragmentByTag(EntityIdDialogFragment.TAG) as EntityIdDialogFragment
        return dialog.requireDialog() as AlertDialog
    }

    private fun AlertDialog.submit(value: String) {
        checkNotNull(findViewById<EditText>(android.R.id.edit)).setText(value)
        clickButton(DialogInterface.BUTTON_POSITIVE)
    }

    private fun AlertDialog.clickButton(which: Int) {
        getButton(which).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
