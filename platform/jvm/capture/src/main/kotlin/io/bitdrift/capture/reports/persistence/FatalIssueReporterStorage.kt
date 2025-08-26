// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import io.bitdrift.capture.reports.binformat.v1.ReportType
import java.io.File
import java.util.UUID

internal class FatalIssueReporterStorage(
    private val destinationDirectory: File,
) : IFatalIssueReporterStorage {
    override fun persistFatalIssue(
        terminationTimeStampInMilli: Long,
        data: ByteArray,
        reportType: Byte,
    ) {
        val fileName = "${terminationTimeStampInMilli}_${mapToReadableType(reportType)}_${UUID.randomUUID()}.cap"
        val outputFile = File(destinationDirectory, fileName)
        outputFile.writeBytes(data)
    }

    override fun generateFilePath(): String = destinationDirectory.path + "/${UUID.randomUUID()}.cap"

    private fun mapToReadableType(reportType: Byte): String =
        when (reportType) {
            ReportType.AppNotResponding -> "anr"
            ReportType.JVMCrash -> "crash"
            ReportType.NativeCrash -> "native_crash"
            else -> "unknown"
        }
}
