// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.IResourceUtilizationTarget
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.events.common.PowerMonitor
import io.bitdrift.capture.providers.toFields
import java.util.concurrent.ExecutorService
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Suppress("UnsafePutAllUsage")
internal class ResourceUtilizationTarget(
    private val memoryMetricsProvider: IMemoryMetricsProvider,
    private val batteryMonitor: BatteryMonitor,
    private val powerMonitor: PowerMonitor,
    private val diskUsageMonitor: DiskUsageMonitor,
    private val errorHandler: ErrorHandler,
    private val logger: LoggerImpl,
    private val executor: ExecutorService,
    private val clock: IClock = DefaultClock.getInstance(),
) : IResourceUtilizationTarget {
    override fun tick() {
        executor.execute {
            try {
                val start = clock.elapsedRealtime()
                val memorySnapshot = memoryMetricsProvider.getMemoryAttributes()
                val fields =
                    buildMap {
                        putAll(memorySnapshot)
                        putAll(diskUsageMonitor.getDiskUsage())
                        putPair(batteryMonitor.batteryPercentageAttribute())
                        putPair(batteryMonitor.isBatteryChargingAttribute())
                        putPair(powerMonitor.isPowerSaveModeEnabledAttribute())
                    }

                val duration = clock.elapsedRealtime() - start
                logger.logResourceUtilization(fields, duration.toDuration(DurationUnit.MILLISECONDS))
                if (memoryMetricsProvider.isMemoryLow()) {
                    logMemoryPressure(memorySnapshot)
                }
            } catch (e: Throwable) {
                errorHandler.handleError("resource utilization tick", e)
            }
        }
    }

    private fun logMemoryPressure(memAttributes: Map<String, String>) {
        logger.log(
            LogType.LIFECYCLE,
            LogLevel.WARNING,
            memAttributes.toFields(),
        ) { "AppMemPressure" }
    }
}

internal fun MutableMap<String, String>.putPair(pair: Pair<String, String>): MutableMap<String, String> {
    this[pair.first] = pair.second
    return this
}
