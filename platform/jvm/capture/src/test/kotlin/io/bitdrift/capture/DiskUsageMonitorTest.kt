// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.events.performance.DiskUsageMonitor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class DiskUsageMonitorTest {
    private val clock: IClock = mock()
    private val preferences: IPreferences = MockPreferences()

    private lateinit var diskUsageMonitor: DiskUsageMonitor

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        diskUsageMonitor = DiskUsageMonitor(
            preferences = preferences,
            context = ContextHolder.APP_CONTEXT,
            clock = clock,
        )
    }

    @Test
    fun emitsDiskUsageFieldsOnceEvery24h() {
        val now = 0L
        whenever(clock.elapsedRealtime()).thenReturn(now)

        val fields1 = diskUsageMonitor.getDiskUsage()
        assertThat(fields1.isNotEmpty()).isTrue()

        // The disk usage fields are emitted only once every 24h.
        val fields2 = diskUsageMonitor.getDiskUsage()
        assertThat(fields2.isEmpty()).isTrue()

        // Move 24h into the future.
        whenever(clock.elapsedRealtime()).thenReturn(now + 60 * 60 * 24 * 1_000)

        val fields3 = diskUsageMonitor.getDiskUsage()
        assertThat(fields3.isNotEmpty()).isTrue()
    }
}
