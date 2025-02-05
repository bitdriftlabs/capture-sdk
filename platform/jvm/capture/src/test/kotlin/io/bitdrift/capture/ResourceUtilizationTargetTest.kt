// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.refEq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.events.common.PowerMonitor
import io.bitdrift.capture.events.performance.BatteryMonitor
import io.bitdrift.capture.events.performance.DiskUsageMonitor
import io.bitdrift.capture.events.performance.ResourceUtilizationTarget
import io.bitdrift.capture.fakes.FakeMemoryMetricsProvider
import org.junit.After
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class ResourceUtilizationTargetTest {
    private val memoryMetricsProvider = FakeMemoryMetricsProvider()
    private val batteryMonitor: BatteryMonitor = mock()
    private val powerMonitor: PowerMonitor = mock()
    private val diskUsageMonitor: DiskUsageMonitor = mock()
    private val errorHandler: ErrorHandler = mock()
    private val logger: LoggerImpl = mock()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
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

    init {
        CaptureJniLibrary.load()
    }

    @Test
    fun resourceUtilizationTargetDoesNotCrash() {
        CaptureTestJniLibrary.runResourceUtilizationTargetTest(reporter)
    }

    @Test
    @Suppress("INVISIBLE_MEMBER")
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
                    "_native_used_kb" to "200",
                    "_native_total_kb" to "500",
                    "_memory_class" to "1024",
                    "_battery_val" to "0.75",
                    "_state" to "charging",
                    "_low_power_enabled" to "1",
                ),
            ),
            // workaround for Cannot invoke NullPointerException: "kotlin.time.Duration.unbox-impl()"
            // from https://stackoverflow.com/a/57394480
            Duration(any<Long>()),
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
