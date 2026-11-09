// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.common

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi

internal class PowerMonitor(
    context: Context,
) {
    // Customers have reported encountering certain devices (particularly the Caterpillar S48C phone)
    // where this returns null on Android 8.1.0. Even though it should always be available on API level >= 21.
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    @RequiresApi(Build.VERSION_CODES.Q)
    private val thermalStatusMap =
        hashMapOf(
            PowerManager.THERMAL_STATUS_NONE to "NONE",
            PowerManager.THERMAL_STATUS_LIGHT to "LIGHT",
            PowerManager.THERMAL_STATUS_MODERATE to "MODERATE",
            PowerManager.THERMAL_STATUS_SEVERE to "SEVERE",
            PowerManager.THERMAL_STATUS_CRITICAL to "CRITICAL",
            PowerManager.THERMAL_STATUS_EMERGENCY to "EMERGENCY",
            PowerManager.THERMAL_STATUS_SHUTDOWN to "SHUTDOWN",
        )

    @RequiresApi(Build.VERSION_CODES.Q)
    fun toThermalStatusString(thermalStatus: Int): String = thermalStatusMap[thermalStatus] ?: "UNKNOWN"

    fun isPowerSaveModeEnabledAttribute(): Pair<String, String> =
        Pair("_low_power_enabled", if (powerManager?.isPowerSaveMode == true) "1" else "0")
}
