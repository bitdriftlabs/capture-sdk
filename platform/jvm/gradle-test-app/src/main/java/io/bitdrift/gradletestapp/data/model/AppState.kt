// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.model

import io.bitdrift.gradletestapp.init.AppProfiler

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
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface ProfilingState {
    data object Idle : ProfilingState

    data class Running(
        val type: AppProfiler.ProfilingType,
    ) : ProfilingState

    data class Saved(
        val type: AppProfiler.ProfilingType,
        val path: String,
    ) : ProfilingState

    data class Failed(
        val type: AppProfiler.ProfilingType,
        val reason: String,
    ) : ProfilingState
}

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
