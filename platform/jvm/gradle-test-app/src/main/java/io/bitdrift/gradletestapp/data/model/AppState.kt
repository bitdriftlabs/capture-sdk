// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.gradletestapp.data.model

import io.bitdrift.gradletestapp.init.AppProfiler
import io.bitdrift.gradletestapp.init.ProfileExporter

/**
 * Root app UI state composed of feature states and global UI flags
 */
data class AppState(
    val config: ConfigState = ConfigState(),
    val session: SessionState = SessionState(),
    val diagnostics: DiagnosticsState = DiagnosticsState(),
    val globalFields: List<GlobalFieldEntry> = emptyList(),
    val diskPressure: DiskPressureState = DiskPressureState.Loading,
    val profiling: ProfilingState = ProfilingState.Idle,
    val profileExport: ProfileExportState = ProfileExportState(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface ProfilingState {
    data object Idle : ProfilingState

    data class Running(
        val type: AppProfiler.ProfilingType,
    ) : ProfilingState

    /** [adbCommand] reads the profile from a host, and is null until the profile is exported. */
    data class Saved(
        val type: AppProfiler.ProfilingType,
        val path: String,
        val adbCommand: String? = null,
    ) : ProfilingState

    data class Failed(
        val type: AppProfiler.ProfilingType,
        val reason: String,
    ) : ProfilingState
}

/** How adb can read saved profiles on this device, and the outcome of the last export or clear. */
data class ProfileExportState(
    val access: ProfileExporter.AdbAccess = ProfileExporter.AdbAccess.EXPORT,
    val status: String? = null,
    val adbCommand: String? = null,
)

sealed interface DiskPressureState {
    data object Loading : DiskPressureState

    data object UnsupportedDevice : DiskPressureState

    data class Ready(
        val availableBytes: Long,
    ) : DiskPressureState

    data class Filling(
        val availableBytes: Long,
    ) : DiskPressureState

    data class Failed(
        val availableBytes: Long,
        val message: String,
    ) : DiskPressureState
}
