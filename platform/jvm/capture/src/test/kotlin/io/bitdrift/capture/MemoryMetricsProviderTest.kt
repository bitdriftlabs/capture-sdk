// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.performance.MemoryMetricsProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class MemoryMetricsProviderTest {
    private val runtime: Runtime = mock()

    @Test
    fun appMemPressureDisabled_NotIncludeFields() {
        // arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryMetricsProvider = MemoryMetricsProvider(activityManager)
        memoryMetricsProvider.runtime = runtime
        whenever(runtime.isEnabled(RuntimeFeature.APP_MEMORY_PRESSURE)).thenReturn(false)

        // act
        val result = memoryMetricsProvider.getMemoryAttributes()

        // assert
        assertThat(result).doesNotContainKeys("_threshold_mem_kb", "_is_memory_low")
    }

    @Test
    fun appMemPressureEnabled_IncludesAllFields() {
        // arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryMetricsProvider = MemoryMetricsProvider(activityManager)
        memoryMetricsProvider.runtime = runtime
        whenever(runtime.isEnabled(RuntimeFeature.APP_MEMORY_PRESSURE)).thenReturn(true)

        // act
        val result = memoryMetricsProvider.getMemoryAttributes()

        // assert
        assertThat(result).containsKeys(
            "_jvm_used_kb",
            "_jvm_total_kb",
            "_jvm_max_kb",
            "_native_used_kb",
            "_native_total_kb",
            "_memory_class",
            "_threshold_mem_kb",
            "_is_memory_low",
        )
    }
}
