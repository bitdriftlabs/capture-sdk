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
     * Represents initialization states for [io.bitdrift.capture.reports.FatalIssueMechanism.BuiltIn]
     */
    sealed class BuiltIn(
        override val readableType: String,
    ) : FatalIssueReporterState(readableType) {
        /**
         * Represents the initialized state when [io.bitdrift.capture.reports.FatalIssueMechanism.BuiltIn] is configured
         */
        data object Initialized : BuiltIn("BUILT_IN_MODE_INITIALIZED")

        /**
         * Represents the failed initialization state when [io.bitdrift.capture.reports.FatalIssueMechanism.BuiltIn] is configured
         */
        data object InitializationFailed : BuiltIn("BUILT_IN_MODE_FAILED")
    }
}
