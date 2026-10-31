// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.reports.persistence

/**
 * Persists an issue report to disk.
 */
internal interface IIssueReporterStore {
    fun persistFatalIssue(
        terminationTimeStampInMilli: Long,
        data: ByteArray,
        reportType: Byte,
    )

    fun persistNonFatalIssue(
        terminationTimeStampInMilli: Long,
        data: ByteArray,
        reportType: Byte,
    )

    fun generateFatalIssueFilePath(): String

    fun generateNonFatalIssueFilePath(): String
}
