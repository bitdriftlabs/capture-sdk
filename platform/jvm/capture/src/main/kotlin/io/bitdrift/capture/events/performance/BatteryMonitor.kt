// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

internal class BatteryMonitor(
    private val context: Context,
) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun batteryValAttribute(): Pair<String, String?> = "_battery_val" to batteryLevelCapacity()?.let { (it / 100.0f).toString() }

    fun batteryLevelAttribute(): Pair<String, String?> = "_battery_level" to batteryLevelCapacity()?.toString()

    fun isBatteryChargingAttribute(): Pair<String, String> = Pair("_state", if (isBatteryCharging()) "charging" else "unplugged")

    private fun isBatteryCharging(): Boolean {
        val batteryStatus: Intent? =
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    // BatteryManager.getIntProperty returns Integer.MIN_VALUE (API 28+) or 0 (older APIs)
    // when the value cannot be determined. Return null to avoid emitting bogus battery fields.
    private fun batteryLevelCapacity(): Int? {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..100) level else null
    }
}
