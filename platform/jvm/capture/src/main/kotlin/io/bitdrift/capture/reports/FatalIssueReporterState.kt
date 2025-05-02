package io.bitdrift.capture.reports

/**
 * Represents all different states for [io.bitdrift.capture.reports.FatalIssueReporter.initialize]
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
     * Represents states for built-in initialization
     */
    sealed class BuiltIn(
        override val readableType: String,
    ) : FatalIssueReporterState(readableType) {
        /**
         * Represents the initialized state when [FatalIssueMechanism.BuiltIn] is configured
         */
        data object Initialized : BuiltIn("BUILT_IN_MODE_INITIALIZED")
    }

    /**
     * Represents states for integration initialization
     */
    sealed class Integration(
        override val readableType: String,
    ) : FatalIssueReporterState(readableType) {
        /**
         * State indicating that prior crash report was sent
         */
        data object FatalIssueReportSent : Integration("INTEGRATION_REPORT_SENT")

        /**
         * State indicating that there are no prior crashes to report
         */
        data object WithoutPriorFatalIssue : Integration("INTEGRATION_NO_PRIOR_ISSUES")

        /**
         * State indicating that the configured crash directory does not exist
         */
        data object InvalidConfigDirectory : Integration("INTEGRATION_INVALID_CONFIG_DIRECTORY")

        /**
         * State indicating that the crash report configuration file is missing
         */
        data object MissingConfigFile : Integration("INTEGRATION_MISSING_CONFIG_FILE")

        /**
         * State indicating that the crash report configuration file content is incorrect
         */
        data object MalformedConfigFile : Integration("INTEGRATION_MALFORMED_CONFIG_FILE")
    }

    /**
     * State indicating that processing fatal issue reports failed
     */
    data class ProcessingFailure(
        /**
         * The mechanism for the processing failure
         */
        val fatalIssueMechanism: FatalIssueMechanism,
        /**
         * The detailed error message
         */
        val errorMessage: String,
    ) : FatalIssueReporterState("${fatalIssueMechanism.displayName}_PROCESSING_FAILURE: $errorMessage")
}
