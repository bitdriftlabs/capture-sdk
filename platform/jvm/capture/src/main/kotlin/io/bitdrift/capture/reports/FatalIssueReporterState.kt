// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

/**
 * Represents all different states for [FatalIssueReporter.processPriorReportFiles]
 */
internal sealed class FatalIssueReporterState(
    open val readableType: String,
) {
    /**
     * Indicates that initial setup call is in progress
     */
    data object Initializing : FatalIssueReporterState("INITIALIZING")

    /**
     * State indicating that crash reporting has not been initialized
     */
    data object NotInitialized : FatalIssueReporterState("NOT_INITIALIZED")

    /**
     * Sealed class representing all initialized states
     */
    sealed class Initialized(
        override val readableType: String,
    ) : FatalIssueReporterState(readableType) {
        /**
         * State indicating that prior crash report was sent
         */
        data object FatalIssueReportSent : Initialized("CRASH_REPORT_SENT")

        /**
         * State indicating that there are no prior crashes to report
         */
        data object WithoutPriorFatalIssue : Initialized("NO_PRIOR_CRASHES")

        /**
         * State indicating that the configured crash directory does not exist
         */
        data object InvalidCrashConfigDirectory : Initialized("INVALID_CRASH_CONFIG_DIRECTORY")

        /**
         * State indicating that the crash report configuration file is missing
         */
        data object MissingConfigFile : Initialized("MISSING_CRASH_CONFIG_FILE")

        /**
         * State indicating that the crash report configuration file content is incorrect
         */
        data object MalformedConfigFile : Initialized("MALFORMED_CRASH_CONFIG_FILE")

        /**
         * State indicating that processing crash reports failed
         */
        data class ProcessingFailure(
            val errorMessage: String,
        ) : Initialized("CRASH_PROCESSING_FAILURE")
    }
}
