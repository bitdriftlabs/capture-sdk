// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.replay.SessionReplayConfiguration

/**
 * A configuration object representing the feature set enabled for Capture.
 * @param sessionReplayConfiguration The resource reporting configuration to use. Passing `null` disables the feature.
 * @param enableFatalIssueReporting When set to true wil capture Fatal Issues automatically [JVM crash, ANR, etc] and without requiring
 * any external 3rd party library integration
 * @param enableNativeCrashReporting When set to true will capture native NDK crashes automatically.
 * Requires enableFatalIssueReporting to be true. Note: This is a temporary flag and may be removed in future versions.
 * @param sleepMode SleepMode.ACTIVE if Capture should initialize in minimal activity mode
 * @param appIdSuffix A suffix that will be appended to the representation of the
 * "app_id" (aka package name). This is useful if you have different environments or
 * build flavors with the same application id and you want a way to target workflows
 * directly to those.
 */
data class Configuration
    @JvmOverloads
    constructor(
        val sessionReplayConfiguration: SessionReplayConfiguration = SessionReplayConfiguration(),
        val enableFatalIssueReporting: Boolean = false,
        val enableNativeCrashReporting: Boolean = false,
        val sleepMode: SleepMode = SleepMode.INACTIVE,
        val appIdSuffix: String = "",
    )
