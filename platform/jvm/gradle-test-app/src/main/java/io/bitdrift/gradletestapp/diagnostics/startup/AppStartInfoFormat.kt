// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.startup

import android.app.ApplicationStartInfo.*

internal fun Int.toStartTypeText(): String =
    when (this) {
        START_TYPE_UNSET -> "START_TYPE_UNSET"
        START_TYPE_COLD -> "START_TYPE_COLD"
        START_TYPE_WARM -> "START_TYPE_WARM"
        START_TYPE_HOT -> "START_TYPE_HOT"
        else -> "UNKNOWN"
    }

internal fun Int.toStartupStateText(): String =
    when (this) {
        STARTUP_STATE_STARTED -> "STARTUP_STATE_STARTED"
        STARTUP_STATE_ERROR -> "STARTUP_STATE_ERROR"
        STARTUP_STATE_FIRST_FRAME_DRAWN -> "STARTUP_STATE_FIRST_FRAME_DRAWN"
        else -> "UNKNOWN"
    }

internal fun Int.toLaunchModeText(): String =
    when (this) {
        LAUNCH_MODE_STANDARD -> "LAUNCH_MODE_STANDARD"
        LAUNCH_MODE_SINGLE_TOP -> "LAUNCH_MODE_SINGLE_TOP"
        LAUNCH_MODE_SINGLE_INSTANCE -> "LAUNCH_MODE_SINGLE_INSTANCE"
        LAUNCH_MODE_SINGLE_TASK -> "LAUNCH_MODE_SINGLE_TASK"
        LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK -> "LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK"
        else -> "UNKNOWN"
    }

internal fun Int.toStartReasonText(): String =
    when (this) {
        START_REASON_ALARM -> "START_REASON_ALARM"
        START_REASON_BACKUP -> "START_REASON_BACKUP"
        START_REASON_BOOT_COMPLETE -> "START_REASON_BOOT_COMPLETE"
        START_REASON_BROADCAST -> "START_REASON_BROADCAST"
        START_REASON_CONTENT_PROVIDER -> "START_REASON_CONTENT_PROVIDER"
        START_REASON_JOB -> "START_REASON_JOB"
        START_REASON_LAUNCHER -> "START_REASON_LAUNCHER"
        START_REASON_LAUNCHER_RECENTS -> "START_REASON_LAUNCHER_RECENTS"
        START_REASON_OTHER -> "START_REASON_OTHER"
        START_REASON_PUSH -> "START_REASON_PUSH"
        START_REASON_SERVICE -> "START_REASON_SERVICE"
        START_REASON_START_ACTIVITY -> "START_REASON_START_ACTIVITY"
        else -> "UNKNOWN"
    }
