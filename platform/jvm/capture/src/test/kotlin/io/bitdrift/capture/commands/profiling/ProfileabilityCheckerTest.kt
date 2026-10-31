// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.commands.profiling

import android.content.Context
import android.content.pm.ApplicationInfo
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.utils.BuildVersionChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.verifyNoInteractions

class ProfileabilityCheckerTest {
    private val context: Context = mock()
    private val buildVersionChecker: BuildVersionChecker = mock()

    @Test
    fun belowAndroid15PassesWithoutReadingTheManifest() {
        whenever(buildVersionChecker.isAtLeast(35)).thenReturn(false)

        assertThat(ProfileabilityChecker(context, buildVersionChecker).isProfileable()).isTrue()
        verifyNoInteractions(context)
    }

    @Test
    fun fromAndroid15ReportsTheManifestSetting() {
        whenever(buildVersionChecker.isAtLeast(35)).thenReturn(true)
        val applicationInfo: ApplicationInfo = mock()
        whenever(context.applicationInfo).thenReturn(applicationInfo)
        whenever(applicationInfo.isProfileable).thenReturn(false)

        assertThat(ProfileabilityChecker(context, buildVersionChecker).isProfileable()).isFalse()
    }
}
