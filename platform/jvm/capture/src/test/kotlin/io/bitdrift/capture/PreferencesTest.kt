// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.content.Context
import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class PreferencesTest {
    private val context: Context = mock()
    private val storage: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()

    @Before
    fun setUp() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(storage)
        whenever(storage.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
    }

    @Test
    fun blockingWritesAndRemovalsReturnTheCommitResult() {
        val preferences = Preferences(context)
        for (committed in listOf(false, true)) {
            whenever(editor.commit()).thenReturn(committed)
            for (value in listOf("owned", null)) {
                assertThat(preferences.setStringBlocking("ownership", value)).isEqualTo(committed)
            }
        }
        verify(editor, never()).apply()
    }

    @Test
    fun asynchronousWritesStillUseApply() {
        Preferences(context).setString("ownership", "owned", blocking = false)

        verify(editor).apply()
        verify(editor, never()).commit()
    }

    @Test
    fun legacyBlockingWritesKeepTheirVoidContract() {
        whenever(editor.commit()).thenReturn(false)

        Preferences(context).setString("ownership", "owned", blocking = true)

        verify(editor).commit()
        verify(editor, never()).apply()
    }
}
