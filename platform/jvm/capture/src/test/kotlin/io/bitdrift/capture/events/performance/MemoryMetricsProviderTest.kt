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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MemoryMetricsProviderTest {
    private val runtime: Runtime = mock()
    private val jvmMemoryProvider: JvmMemoryProvider = mock()
    private val processMemoryProvider: ProcessMemoryProvider = mock()

    private lateinit var memoryMetricsProvider: MemoryMetricsProvider

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        memoryMetricsProvider =
            MemoryMetricsProvider(
                activityManager,
                jvmMemoryProvider = jvmMemoryProvider,
                processMemoryProvider = processMemoryProvider,
            )
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
    fun getMemoryAttributes_withProcessMemory_includesAnonRssAndSwap() {
        whenever(processMemoryProvider.processMemory()).thenReturn(ProcessMemory(anonRssKb = 1000, swapKb = 234))

        val result = memoryMetricsProvider.getMemoryAttributes()

        assertThat(result["_anon_rss_kb"]).isEqualTo("1000")
        assertThat(result["_swap_kb"]).isEqualTo("234")
        assertThat(result["_anon_rss_swap_kb"]).isEqualTo("1234")
    }

    @Test
    fun getMemoryAttributes_withoutProcessMemory_omitsAnonRssAndSwap() {
        whenever(processMemoryProvider.processMemory()).thenReturn(null)

        val result = memoryMetricsProvider.getMemoryAttributes()

        assertThat(result.keys.toList()).doesNotContain("_anon_rss_kb", "_swap_kb", "_anon_rss_swap_kb")
    }

    @Test
    fun defaultProcessMemoryProvider_parsesRssAnonAndVmSwap() {
        val statusFile = tempFolder.newFile("status")
        statusFile.writeText(
            """
            Name:	io.bitdrift.app
            VmRSS:	  250000 kB
            RssAnon:	  120000 kB
            RssFile:	  125000 kB
            VmSwap:	    3456 kB
            """.trimIndent(),
        )

        val result = DefaultProcessMemoryProvider(statusFile).processMemory()

        assertThat(result).isEqualTo(ProcessMemory(anonRssKb = 120000, swapKb = 3456))
    }

    @Test
    fun defaultProcessMemoryProvider_withoutRssAnon_returnsNull() {
        val statusFile = tempFolder.newFile("status")
        statusFile.writeText("VmRSS:\t  250000 kB\nVmSwap:\t       0 kB\n")

        assertThat(DefaultProcessMemoryProvider(statusFile).processMemory()).isNull()
    }

    @Test
    fun defaultProcessMemoryProvider_withMissingFile_returnsNull() {
        val missingFile = File(tempFolder.root, "missing")

        assertThat(DefaultProcessMemoryProvider(missingFile).processMemory()).isNull()
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

    @Test
    fun getMemoryAttributes_jvmUsedPercent_isLocaleIndependent() {
        val originalLocale = Locale.getDefault()
        try {
            whenever(jvmMemoryProvider.usedMemoryBytes()).thenReturn(16_664L)
            whenever(jvmMemoryProvider.maxMemoryBytes()).thenReturn(100_000L)

            val locales =
                listOf(
                    Locale.US,
                    Locale.UK,
                    Locale.forLanguageTag("ar-EG"),
                    Locale.forLanguageTag("fa-IR"),
                    Locale.FRANCE,
                    Locale.GERMANY,
                    Locale.forLanguageTag("pt-BR"),
                    Locale.forLanguageTag("in-ID"),
                    Locale.forLanguageTag("tr-TR"),
                )

            locales.forEach { locale ->
                Locale.setDefault(locale)
                val result = memoryMetricsProvider.getMemoryAttributes()
                val value = result["_jvm_used_percent"]
                assertThat(value)
                    .withFailMessage("Failed for locale: $locale. Expected 16.664 but got $value")
                    .isEqualTo("16.664")
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
