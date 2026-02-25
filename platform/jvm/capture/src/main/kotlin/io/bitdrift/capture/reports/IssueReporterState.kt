// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

/**
 * Represents all different states for [io.bitdrift.capture.reports.IssueReporter.processPriorReportFiles].
 */
sealed class IssueReporterState(
    /**
     * The readable type that won't be obfuscated for logs
     */
    open val readableType: String,
) {
    /**
     * State indicating that issue reporting has not been initialized
     */
    data object NotInitialized : IssueReporterState("NOT_INITIALIZED")

    /**
     * Initialization is currently in progress
     */
    data object Initializing : IssueReporterState("INITIALIZING")

    /**
     * Represents a successful initialized state
     */
    data object Initialized : IssueReporterState("INITIALIZED")

    /**
     * Represents a failed initialization attempt state
     */
    data object InitializationFailed : IssueReporterState("FAILED_TO_INITIALIZE")

    /**
     * Reporting not enabled because client-side configuration is disabled
     */
    data object ClientDisabled : IssueReporterState("CLIENT_CONFIG_DISABLED")

    /**
     * Runtime configuration states
     */
    sealed class RuntimeState(
        override val readableType: String,
    ) : IssueReporterState(readableType) {
        /**
         * Reporting enabled because server-side configuration is enabled
         */
        data object Enabled : RuntimeState("RUNTIME_CONFIG_ENABLED")

        /**
         * Reporting not enabled because server-side configuration is disabled
         */
        data object Disabled : RuntimeState("RUNTIME_CONFIG_DISABLED")

        /**
         * Reporting not enabled because server-side configuration is unset
         */
        data object Unset : RuntimeState("RUNTIME_CONFIG_UNSET")

        /**
         * Reporting not enabled because server-side configuration is corrupted
         */
        data object Invalid : RuntimeState("RUNTIME_CONFIG_INVALID")
    }
}
