// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

/**
 * Specifies the fatal issue reporting mechanism. mechanism.
 *
 */
enum class FatalIssueMechanism(
    /**
     * The readable name that can be send via [InternalFieldsLog]
     */
    val displayName: String,
) {
    /**
     * Use this option, to integrate with existing fatal issue reporting mechanism. This will scan for specific
     * fatal issues on the configured directory
     */
    INTEGRATION("INTEGRATION"),

    /**
     * Built in implementation that doesn't rely on any 3rd party integration
     */
    BUILT_IN("BUILT_IN"),

    /**
     * This is the default if [Capture.Logger.initFatalIssueReporting] never called
     */
    NONE("NONE"),
}
