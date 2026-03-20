// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.app.ApplicationExitInfo

/**
 * Application termination reason values.
 */
enum class ExitReason(
    /** Stable text representation safe for serialization/logging. */
    val value: String,
) {
    /** App exited itself. */
    ExitSelf("EXIT_SELF"),

    /** App was terminated by a signal. */
    Signaled("SIGNALED"),

    /** App exited due to low memory. */
    LowMemory("LOW_MEMORY"),

    /** App exited due to a JVM crash. */
    JvmCrash("CRASH"),

    /** App exited due to a native crash. */
    NativeCrash("CRASH_NATIVE"),

    /** App exited due to ANR. */
    AppNotResponding("ANR"),

    /** App exited due to initialization failure. */
    InitializationFailure("INITIALIZATION_FAILURE"),

    /** App exited due to permission changes. */
    PermissionChange("PERMISSION_CHANGE"),

    /** App exited due to excessive resource usage. */
    ExcessiveResourceUsage("EXCESSIVE_RESOURCE_USAGE"),

    /** App exited due to a user request. */
    UserRequested("USER_REQUESTED"),

    /** App was stopped by the user. */
    UserStopped("USER_STOPPED"),

    /** App exited because a dependency died. */
    DependencyDied("DEPENDENCY_DIED"),

    /** Other OS exit reason. */
    Other("OTHER"),

    /** App was frozen by the OS. */
    Freezer("FREEZER"),

    /** Unknown or unsupported reason. */
    Unknown("UNKNOWN"),

    ;

    /**
     * Lookup helpers for [ExitReason].
     */
    companion object {
        /**
         * Returns the enum value matching a stable string representation.
         */
        fun fromValue(value: String): ExitReason? = entries.firstOrNull { it.value == value }
    }
}

internal fun Int.toExitReason(): ExitReason =
    when (this) {
        ApplicationExitInfo.REASON_EXIT_SELF -> ExitReason.ExitSelf
        ApplicationExitInfo.REASON_SIGNALED -> ExitReason.Signaled
        ApplicationExitInfo.REASON_LOW_MEMORY -> ExitReason.LowMemory
        ApplicationExitInfo.REASON_CRASH -> ExitReason.JvmCrash
        ApplicationExitInfo.REASON_CRASH_NATIVE -> ExitReason.NativeCrash
        ApplicationExitInfo.REASON_ANR -> ExitReason.AppNotResponding
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> ExitReason.InitializationFailure
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> ExitReason.PermissionChange
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> ExitReason.ExcessiveResourceUsage
        ApplicationExitInfo.REASON_USER_REQUESTED -> ExitReason.UserRequested
        ApplicationExitInfo.REASON_USER_STOPPED -> ExitReason.UserStopped
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> ExitReason.DependencyDied
        ApplicationExitInfo.REASON_OTHER -> ExitReason.Other
        ApplicationExitInfo.REASON_FREEZER -> ExitReason.Freezer
        else -> ExitReason.Unknown
    }
