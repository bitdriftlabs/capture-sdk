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

    /**
     * Whether Dropped Frames reporting is enabled
     */
    data object DROPPED_EVENTS_MONITORING : RuntimeFeature("client_feature.android.dropped_frames_reporting")
}

/**
 * Known runtime config values.
 * @param configName the runtime key to use for this config value
 * @param defaultValue The value
 */
sealed class RuntimeConfig(
    val configName: String,
    val defaultValue: Int,
) {
    /**
     * The configured value for [jankHeuristicMultiplier] from [JankStatsMonitoring]. More info at https://developer.android.com/topic/performance/jankstats#jank-heuristics
     */
    data object JANK_FRAME_HEURISTICS_MULTIPLIER : RuntimeConfig("client_feature.android.jank_frame_heuristics_multiplier", 2)

    /**
     * The upper bound threshold that defines what constitutes a FROZEN frame reported via [JankStats]
     *
     * The default value is 700ms
     *
     * Slow Frame: >=16ms to < FROZEN_FRAME_THRESHOLD_IN_MILLI_SECONDS
     * Frozen Frame: >=FROZEN_FRAME_THRESHOLD_IN_MILLI_SECONDS to <ANR_FRAME_THRESHOLD_IN_MILLI_SECONDS
     * ANR Frame: >=ANR_FRAME_THRESHOLD_IN_MILLI_SECONDS
     */
    data object FROZEN_FRAME_THRESHOLD_IN_MILLI_SECONDS : RuntimeConfig("client_feature.android.frozen_frame.threshold_ms", 700)

    /**
     * The upper bound threshold that defines what constitutes an ANR frame reported via [JankStats]
     *
     * The default value is 5000ms
     *
     * Slow Frame: >=16ms to < FROZEN_FRAME_THRESHOLD_IN_MILLI_SECONDS
     * Frozen Frame: >=FROZEN_FRAME_THRESHOLD_IN_MILLI_SECONDS to <ANR_FRAME_THRESHOLD_IN_MILLI_SECONDS
     * ANR Frame: >=ANR_FRAME_THRESHOLD_IN_MILLI_SECONDS
     */
    data object ANR_FRAME_THRESHOLD_IN_MILLI_SECONDS : RuntimeConfig("client_feature.android.application_anr_reporting.threshold_ms, 5000)
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

    /**
     * Returns the configured Int value
     * @param config the configuration value to check
     */
    fun getConfigValue(config: RuntimeConfig): Int
}
