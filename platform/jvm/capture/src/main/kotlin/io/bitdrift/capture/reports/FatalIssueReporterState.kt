// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

/**
 * Represents all different states for [io.bitdrift.capture.reports.FatalIssueReporter.processPriorReportFiles]
 */
sealed class FatalIssueReporterState(
    /**
     * The readable type that won't be obfuscated for logs
     */
    open val readableType: String,
) {
    /**
     * State indicating that crash reporting has not been initialized
     */
    data object NotInitialized : FatalIssueReporterState("NOT_INITIALIZED")

    /**
     * Initialization is currently in progress
     */
    data object Initializing : FatalIssueReporterState("INITIALIZING")

    /**
     * Represents a successful initialized state
     */
    data object Initialized : FatalIssueReporterState("INITIALIZED")

    /**
     * Represents a failed initialization attempt state
     */
    data object InitializationFailed : FatalIssueReporterState("FAILED_TO_INITIALIZED")

    /**
     * Reporting not enabled because server-side configuration is disabled or unset
     */
    data object RuntimeDisabled : FatalIssueReporterState("RUNTIME_CONFIG_DISABLED")
}
