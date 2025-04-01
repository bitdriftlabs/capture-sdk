package io.bitdrift.capture.reports

/**
 * TBF
 */
enum class FatalIssueMechanism(
    /**
     * TBF
     */
    val displayName: String,
) {
    /**
     * TBF
     */
    CUSTOM_CONFIG("CUSTOM_CONFIG"),

    /**
     * TBF
     */
    BUILT_IN("BUILT_IN"),

    /**
     * This is the default if there are no prior calls to Capture.Logger.start.initReporter
     */
    NONE("NONE"),
}
