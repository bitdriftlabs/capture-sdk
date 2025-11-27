// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.refEq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.CaptureTestJniLibrary
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.events.common.PowerMonitor
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider
import io.bitdrift.capture.providers.toFields
import org.junit.After
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ResourceUtilizationTargetTest {
    private val memoryMetricsProvider = FakeMemoryMetricsProvider()
    private val batteryMonitor: BatteryMonitor = mock()
    private val powerMonitor: PowerMonitor = mock()
    private val diskUsageMonitor: DiskUsageMonitor = mock()
    private val errorHandler: ErrorHandler = mock()
    private val logger: LoggerImpl = mock()
    private val executor: ExecutorService = MoreExecutors.newDirectExecutorService()
    private val clock: IClock = mock()

    private val reporter =
        ResourceUtilizationTarget(
            memoryMetricsProvider = memoryMetricsProvider,
            batteryMonitor = batteryMonitor,
            powerMonitor = powerMonitor,
            diskUsageMonitor = diskUsageMonitor,
            errorHandler = errorHandler,
            logger = logger,
            executor = executor,
            clock = clock,
        )

    @Test
    fun resourceUtilizationTargetDoesNotCrash() {
        CaptureJniLibrary.load()
        CaptureTestJniLibrary.runResourceUtilizationTargetTest(reporter)
    }

    @Test
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    fun resourceUtilizationTickEmitsLog() {
        whenever(batteryMonitor.batteryPercentageAttribute()).thenReturn(Pair("_battery_val", "0.75"))
        whenever(batteryMonitor.isBatteryChargingAttribute()).thenReturn(Pair("_state", "charging"))

        whenever(powerMonitor.isPowerSaveModeEnabledAttribute()).thenReturn(Pair("_low_power_enabled", "1"))

        reporter.tick()

        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify(logger).logResourceUtilization(
            eq(
                mapOf(
                    "_jvm_used_kb" to "50",
                    "_jvm_total_kb" to "100",
                    "_jvm_max_kb" to "100",
                    "_jvm_used_percent" to "50",
                    "_native_used_kb" to "200",
                    "_native_total_kb" to "500",
                    "_memory_class" to "1",
                    "_battery_val" to "0.75",
                    "_state" to "charging",
                    "_low_power_enabled" to "1",
                ),
            ),
            // workaround for Cannot invoke NullPointerException: "kotlin.time.Duration.unbox-impl()"
            // from https://stackoverflow.com/a/57394480
            Duration(any<Long>()),
        )

        // no AppMemPressure log by default
        verify(logger, never()).log(any(), any(), any(), any())
    }

    @Test
    fun resourceUtilizationTickEmitsAppMemPressureLog() {
        memoryMetricsProvider.setIsMemoryLow(true)

        whenever(batteryMonitor.batteryPercentageAttribute()).thenReturn(Pair("_battery_val", "0.75"))
        whenever(batteryMonitor.isBatteryChargingAttribute()).thenReturn(Pair("_state", "charging"))
        whenever(powerMonitor.isPowerSaveModeEnabledAttribute()).thenReturn(Pair("_low_power_enabled", "1"))

        reporter.tick()

        executor.awaitTermination(1, TimeUnit.SECONDS)

        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.WARNING),
            eq(
                mapOf(
                    "_jvm_used_kb" to "50",
                    "_jvm_total_kb" to "100",
                    "_jvm_max_kb" to "100",
                    "_jvm_used_percent" to "50",
                    "_native_used_kb" to "200",
                    "_native_total_kb" to "500",
                    "_memory_class" to "1",
                ).toFields(),
            ),
            eq(null),
            eq(null),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppMemPressure" },
        )
    }

    @Test
    fun resourceUtilizationTickSnapshotFails() {
        val exception = IllegalArgumentException()
        memoryMetricsProvider.setException(exception)

        reporter.tick()

        executor.awaitTermination(1, TimeUnit.SECONDS)
        verify(errorHandler).handleError(eq("resource utilization tick"), refEq(exception))
    }

    @After
    fun tearDown() {
        memoryMetricsProvider.clear()
    }
}
