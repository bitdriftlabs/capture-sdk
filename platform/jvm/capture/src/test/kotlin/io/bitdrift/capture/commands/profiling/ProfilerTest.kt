// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.profiling

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.utils.BuildVersionChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.verifyNoInteractions

class ProfilerTest {
    private val context: Context = mock()
    private val buildVersionChecker: BuildVersionChecker = mock()

    @Test
    fun belowAndroid15ReportsUnsupportedWithoutTouchingThePlatform() {
        whenever(buildVersionChecker.isAtLeast(35)).thenReturn(false)
        val outcomes = mutableListOf<ProfileOutcome>()

        Profiler(context, Runnable::run, buildVersionChecker).request(REQUEST) { outcomes += it }

        assertThat(outcomes).containsExactly(
            ProfileOutcome.Failed(0, "unsupported", "ProfilingManager requires Android 15 or newer"),
        )
        verifyNoInteractions(context)
    }

    private companion object {
        val REQUEST =
            ProfileRequest(
                requestId = 1,
                type = ProfileType.STACK_SAMPLING,
                durationMs = 3_000,
                frequencyHz = 100,
                bufferSizeKb = 2_048,
                tag = "bitdrift",
            )
    }
}
