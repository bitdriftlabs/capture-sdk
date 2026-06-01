// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MemoryMetricsProviderTest {
    private val runtime: Runtime = mock()
    private val jvmMemoryProvider: JvmMemoryProvider = mock()

    private lateinit var memoryMetricsProvider: MemoryMetricsProvider

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        memoryMetricsProvider =
            MemoryMetricsProvider(activityManager, jvmMemoryProvider = jvmMemoryProvider)
        memoryMetricsProvider.runtime = runtime
        whenever(runtime.getConfigValue(RuntimeConfig.APP_WARNING_MEMORY_PERCENT_THRESHOLD)).thenReturn(75)
        whenever(runtime.getConfigValue(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)).thenReturn(
            90,
        )
    }

    @Test
    fun getMemoryAttributes_includesAllFields() {
        whenever(runtime.getConfigValue(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)).thenReturn(
            90,
        )

        val result = memoryMetricsProvider.getMemoryAttributes()

        assertThat(result.keys.toList()).containsAll(
            listOf(
                "_jvm_used_kb",
                "_jvm_total_kb",
                "_jvm_max_kb",
                "_jvm_used_percent",
                "_native_used_kb",
                "_native_total_kb",
                "_memory_class",
                "_is_memory_low",
            ),
        )
    }

    @Test
    fun isMemoryLow_configNotAvailable_shouldReturnFalse() {
        memoryMetricsProvider.runtime = null

        val result = memoryMetricsProvider.isMemoryLow()

        assertThat(result).isFalse
    }

    @Test
    fun isMemoryLow_withMinHighThreshold_shouldReturnFalse() {
        whenever(runtime.getConfigValue(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)).thenReturn(
            49,
        )

        val result = memoryMetricsProvider.isMemoryLow()

        assertThat(result).isFalse
    }

    @Test
    fun isMemoryLow_withFakeHighThreshold_shouldReturnFalse() {
        whenever(runtime.getConfigValue(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)).thenReturn(
            200,
        )

        val result = memoryMetricsProvider.isMemoryLow()

        assertThat(result).isFalse
    }

    @Test
    fun isMemoryLow_withThresholdBelowMinimum_shouldReturnFalse() {
        whenever(runtime.getConfigValue(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)).thenReturn(
            0,
        )

        val result = memoryMetricsProvider.isMemoryLow()

        assertThat(result).isFalse
    }

    @Test
    fun isMemoryLow_whenUsageAtThreshold_shouldReturnTrue() {
        whenever(jvmMemoryProvider.usedMemoryBytes()).thenReturn(90_000L)
        whenever(jvmMemoryProvider.maxMemoryBytes()).thenReturn(100_000L)

        val result = memoryMetricsProvider.isMemoryLow()

        assertThat(result).isTrue
    }

    @Test
    fun isMemoryLow_whenUsageBelowThreshold_shouldReturnsFalse() {
        whenever(jvmMemoryProvider.usedMemoryBytes()).thenReturn(89_000L)
        whenever(jvmMemoryProvider.maxMemoryBytes()).thenReturn(100_000L)
        whenever(runtime.getConfigValue(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)).thenReturn(
            90,
        )

        val result = memoryMetricsProvider.isMemoryLow()

        assertThat(result).isFalse
    }

    @Test
    fun getJvmMemoryPressureLevel_whenRuntimeIsNull_shouldReturnUnknown() {
        memoryMetricsProvider.runtime = null

        val result = memoryMetricsProvider.getCurrentJvmMemoryPressureLevel()

        assertThat(result).isEqualTo(MemoryPressureLevel.Unknown)
    }

    @Test
    fun getJvmMemoryPressureLevel_whenBelowWarningThreshold_shouldReturnNormal() {
        memoryMetricsProvider.runtime = runtime
        whenever(jvmMemoryProvider.usedMemoryBytes()).thenReturn(50_000L)
        whenever(jvmMemoryProvider.maxMemoryBytes()).thenReturn(100_000L)

        val result = memoryMetricsProvider.getCurrentJvmMemoryPressureLevel()

        assertThat(result).isEqualTo(MemoryPressureLevel.Normal)
    }

    @Test
    fun getJvmMemoryPressureLevel_whenAtWarningThreshold_shouldReturnWarning() {
        memoryMetricsProvider.runtime = runtime
        whenever(jvmMemoryProvider.usedMemoryBytes()).thenReturn(75_000L)
        whenever(jvmMemoryProvider.maxMemoryBytes()).thenReturn(100_000L)

        val result = memoryMetricsProvider.getCurrentJvmMemoryPressureLevel()

        assertThat(result).isEqualTo(MemoryPressureLevel.Warning)
    }

    @Test
    fun getJvmMemoryPressureLevel_whenBetweenWarningAndCritical_shouldReturnWarning() {
        memoryMetricsProvider.runtime = runtime
        whenever(jvmMemoryProvider.usedMemoryBytes()).thenReturn(85_000L)
        whenever(jvmMemoryProvider.maxMemoryBytes()).thenReturn(100_000L)

        val result = memoryMetricsProvider.getCurrentJvmMemoryPressureLevel()

        assertThat(result).isEqualTo(MemoryPressureLevel.Warning)
    }

    @Test
    fun getJvmMemoryPressureLevel_whenAtCriticalThreshold_shouldReturnCritical() {
        whenever(runtime.getConfigValue(RuntimeConfig.APP_WARNING_MEMORY_PERCENT_THRESHOLD)).thenReturn(70)
        whenever(runtime.getConfigValue(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)).thenReturn(90)
        whenever(jvmMemoryProvider.usedMemoryBytes()).thenReturn(90_000L)
        whenever(jvmMemoryProvider.maxMemoryBytes()).thenReturn(100_000L)

        val result = memoryMetricsProvider.getCurrentJvmMemoryPressureLevel()

        assertThat(result).isEqualTo(MemoryPressureLevel.Critical)
    }

    @Test
    fun getJvmMemoryPressureLevel_whenAboveCriticalThreshold_shouldReturnCritical() {
        whenever(runtime.getConfigValue(RuntimeConfig.APP_WARNING_MEMORY_PERCENT_THRESHOLD)).thenReturn(70)
        whenever(runtime.getConfigValue(RuntimeConfig.APP_CRITICAL_MEMORY_PERCENT_THRESHOLD)).thenReturn(90)
        whenever(jvmMemoryProvider.usedMemoryBytes()).thenReturn(95_000L)
        whenever(jvmMemoryProvider.maxMemoryBytes()).thenReturn(100_000L)

        val result = memoryMetricsProvider.getCurrentJvmMemoryPressureLevel()

        assertThat(result).isEqualTo(MemoryPressureLevel.Critical)
    }
}
