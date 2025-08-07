// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import io.bitdrift.capture.Configuration

/**
 * Factory utility for creating FatalIssueReporter instances based on configuration
 */
internal object FatalIssueReporterFactory {
    /**
     * Creates a FatalIssueReporter instance based on the provided configuration.
     *
     * @param configuration The capture configuration containing fatal issue reporting settings
     * @return FatalIssueReporter instance if enableFatalIssueReporting is true, null otherwise
     */
    fun create(configuration: Configuration): IFatalIssueReporter? =
        if (configuration.enableFatalIssueReporting) {
            FatalIssueReporter(configuration.enableNativeCrashReporting)
        } else {
            null
        }
}
