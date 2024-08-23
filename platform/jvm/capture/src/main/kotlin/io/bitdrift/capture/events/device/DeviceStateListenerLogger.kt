// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.device

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger
import io.bitdrift.capture.events.common.PowerMonitor
import io.bitdrift.capture.events.performance.BatteryMonitor
import io.bitdrift.capture.providers.toFields
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

internal class DeviceStateListenerLogger(
    private val logger: LoggerImpl,
    private val context: Context,
    private val batteryMonitor: BatteryMonitor,
    private val powerMonitor: PowerMonitor,
    private val runtime: Runtime,
    private val executor: ExecutorService,
) : IEventListenerLogger, ComponentCallbacks2, BroadcastReceiver() {

    companion object {
        private const val BATTERY_CHANGE = "BatteryStateChange"
        private const val BATTERY_LOW = "BatteryLowPowerMode"
        private const val ORIENTATION_CHANGE = "OrientationChange"
        private const val TIMEZONE_CHANGE = "TimeZoneChange"
        private const val THERMAL_STATE_CHANGE = "ThermalStateChange"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private val thermalCallback = this::logThermalStatusChanged

    // keep a snapshot of the current configuration in a new instance since Android updates the same reference
    private var prevConfig: AtomicReference<Configuration> = AtomicReference(Configuration(context.resources.configuration))

    override fun start() {
        if (!runtime.isEnabled(RuntimeFeature.DEVICE_STATE_EVENTS)) {
            return
        }

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        context.registerReceiver(this, filter)
        context.registerComponentCallbacks(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerMonitor.powerManager.addThermalStatusListener(executor, thermalCallback)
        }
    }

    override fun stop() {
        context.unregisterReceiver(this)
        context.unregisterComponentCallbacks(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerMonitor.powerManager.removeThermalStatusListener(thermalCallback)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        executor.execute {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> log(
                    mapOf("_state" to "charging", batteryMonitor.batteryPercentageAttribute()),
                    BATTERY_CHANGE,
                )

                Intent.ACTION_POWER_DISCONNECTED -> log(
                    mapOf("_state" to "unplugged", batteryMonitor.batteryPercentageAttribute()),
                    BATTERY_CHANGE,
                )

                Intent.ACTION_TIMEZONE_CHANGED -> log(
                    mapOf("_time_zone" to intent.getStringExtra("time-zone").orEmpty()),
                    TIMEZONE_CHANGE,
                )

                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> log(
                    mapOf(
                        powerMonitor.isPowerSaveModeEnabledAttribute(),
                        batteryMonitor.batteryPercentageAttribute(),
                        batteryMonitor.isBatteryChargingAttribute(),
                    ),
                    BATTERY_LOW,
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        executor.execute {
            val diff = newConfig.diff(prevConfig.get())
            prevConfig.set(Configuration(newConfig))
            // detect whether the configuration change was an orientation change
            if (diff and ActivityInfo.CONFIG_ORIENTATION == ActivityInfo.CONFIG_ORIENTATION) {
                if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    log(mapOf("_orientation" to "landscape"), ORIENTATION_CHANGE)
                } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    log(mapOf("_orientation" to "portrait"), ORIENTATION_CHANGE)
                }
            }
        }
    }

    override fun onLowMemory() {
        // no-op - this is already tracked by [AppMemoryPressureListenerLogger]
    }

    override fun onTrimMemory(level: Int) {
        // no-op - this is already tracked by [AppMemoryPressureListenerLogger]
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun logThermalStatusChanged(status: Int) {
        // No need to run this on the executor since the callback is already using our background executor
        log(mapOf("_thermal_state" to powerMonitor.toThermalStatusString(status)), THERMAL_STATE_CHANGE)
    }

    private fun log(fields: Map<String, String>, message: String) {
        logger.log(LogType.DEVICE, LogLevel.INFO, fields.toFields()) { message }
    }
}
