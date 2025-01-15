// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("ktlint:standard:class-naming")

package io.bitdrift.capture.common

/**
 * Known runtime feature flags.
 * @param featureName the runtime key to use for a certain feature
 * @param defaultValue whether the feature is enabled by default
 */
sealed class RuntimeFeature(
    val featureName: String,
    val defaultValue: Boolean = true,
) {
    /**
     * Whether the session replay feature is enabled for Compose views.
     */
    data object SESSION_REPLAY_COMPOSE : RuntimeFeature("client_feature.android.session_replay_compose")

    /**
     * Whether an app update event emission is enabled or not.
     */
    data object APP_UPDATE_EVENTS : RuntimeFeature("client_feature.android.application_update_reporting")

    /**
     * Whether memory pressure monitoring is enabled.
     */
    data object APP_MEMORY_PRESSURE : RuntimeFeature("client_feature.android.memory_pressure_reporting")

    /**
     * Whether device state monitoring is enabled.
     */
    data object DEVICE_STATE_EVENTS : RuntimeFeature("client_features.android.device_lifecycle_reporting")

    /**
     * Whether application lifecycle monitoring is enabled.
     */
    data object APP_LIFECYCLE_EVENTS : RuntimeFeature("client_feature.android.application_lifecycle_reporting")

    /**
     * Whether application exit monitoring is enabled.
     */
    data object APP_EXIT_EVENTS : RuntimeFeature("client_feature.android.application_exit_reporting")

    /**
     * Whether data disk usage should be reported as part of resource utilization logs.
     */
    data object DISK_USAGE_FIELDS : RuntimeFeature("client_feature.android.disk_usage_reporting", defaultValue = true)

    /**
     * Whether internal logs are forwarded to our on SDK. Disabled by default.
     */
    data object INTERNAL_LOGS : RuntimeFeature("client_feature.android.internal_logs", defaultValue = false)

    /**
     * Whether the logger should be flushed on crash.
     */
    data object LOGGER_FLUSHING_ON_CRASH : RuntimeFeature("client_feature.android.logger_flushing_on_force_quit", defaultValue = true)
}

/**
 * Allows checking whether a runtime feature is enabled. Features may be remotely disabled via the a runtime flag, making it possible to
 * disable features that are known to be problematic with certain SDK versions.
 */
interface Runtime {
    /**
     * Returns true if the provided feature is enabled.
     * @param feature the feature flag to check
     */
    fun isEnabled(feature: RuntimeFeature): Boolean
}
