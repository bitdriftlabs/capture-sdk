// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesTest {
    private val mockContext: Context = mock()
    private val storage: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()

    @Before
    fun setUp() {
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(storage)
        whenever(storage.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
    }

    @Test
    fun blockingWritesAndRemovalsWorkWithAndroidPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Preferences(context)
        val key = "crash_profiles.owned.anr"

        assertThat(preferences.setStringBlocking(key, "anr")).isTrue()
        assertThat(preferences.getString(key)).isEqualTo("anr")
        assertThat(preferences.setStringBlocking(key, null)).isTrue()
        assertThat(preferences.getString(key)).isNull()
    }

    @Test
    fun blockingWritesAndRemovalsReturnCommitFailures() {
        val preferences = Preferences(mockContext)
        whenever(editor.commit()).thenReturn(false)

        assertThat(preferences.setStringBlocking("ownership", "owned")).isFalse()
        assertThat(preferences.setStringBlocking("ownership", null)).isFalse()
        verify(editor, never()).apply()
    }

    @Test
    fun asynchronousWritesStillUseApply() {
        Preferences(mockContext).setString("ownership", "owned", blocking = false)

        verify(editor).apply()
        verify(editor, never()).commit()
    }

    @Test
    fun legacyBlockingWritesKeepTheirVoidContract() {
        whenever(editor.commit()).thenReturn(false)

        Preferences(mockContext).setString("ownership", "owned", blocking = true)

        verify(editor).commit()
        verify(editor, never()).apply()
    }
}
