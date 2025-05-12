// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

/**
 * Persists a fatal issue report to disk
 */
internal interface IFatalIssueReporterStorage {
    fun persistFatalIssue(
        terminationTimeStampInMilli: Long,
        data: ByteArray,
    )
}
