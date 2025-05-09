// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.FatalIssueType

/**
 * Persists a [FatalIssueReport] into disk
 */
internal interface IFatalIssueReporterStorage {
    /**
     * Persist the [FatalIssueType] into disk
     */
    fun persistFatalIssue(
        terminationTimeStampInMilli: Long,
        fatalIssueType: FatalIssueType,
        fatalIssueReport: FatalIssueReport,
    )
}
