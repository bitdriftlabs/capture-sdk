// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import java.io.File
import java.util.UUID

internal class FatalIssueReporterStorage(
    private val destinationDirectory: File,
) : IFatalIssueReporterStorage {
    override fun persistFatalIssue(
        terminationTimeStampInMilli: Long,
        data: ByteArray,
    ) {
        val fileName = "${terminationTimeStampInMilli}_${UUID.randomUUID()}.cap"
        val outputFile = File(destinationDirectory, fileName)
        outputFile.writeBytes(data)
    }
}
