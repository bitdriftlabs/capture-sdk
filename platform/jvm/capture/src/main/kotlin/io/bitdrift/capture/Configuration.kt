// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.network.okhttp.otel.OtelExportConfiguration
import io.bitdrift.capture.replay.SessionReplayConfiguration
import io.bitdrift.capture.reports.IssueCallbackConfiguration

/**
 * A configuration object representing the feature set enabled for Capture.
 * @param sessionReplayConfiguration The resource reporting configuration to use. Passing `null` disables the feature.
 * @param enableFatalIssueReporting When set to true captures fatal issues automatically (JVM crash, ANR, etc.)
 *                                  without requiring third-party integrations.
 * @param sleepMode SleepMode.ENABLED if Capture should initialize in minimal activity mode
 * @param issueCallbackConfiguration Optional callback configuration used for issue report callbacks.
 *                                   This is only effective when [enableFatalIssueReporting] is true.
 * @param otelExportConfiguration Optional configuration for exporting OpenTelemetry spans for
 *                                traced network requests to an external OTLP/HTTP endpoint.
 *                                Passing `null` (the default) disables the feature.
 */
data class Configuration
    @JvmOverloads
    constructor(
        val sessionReplayConfiguration: SessionReplayConfiguration? = SessionReplayConfiguration(),
        val enableFatalIssueReporting: Boolean = true,
        val sleepMode: SleepMode = SleepMode.DISABLED,
        @property:ExperimentalBitdriftApi
        val issueCallbackConfiguration: IssueCallbackConfiguration? = null,
        @property:ExperimentalBitdriftApi
        val otelExportConfiguration: OtelExportConfiguration? = null,
    )
