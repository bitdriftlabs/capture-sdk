// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import java.io.File
import java.util.UUID

internal class IssueReporterStorage(
    sdkDirectory: String,
) : IReporterIssueStorage {
    private val fatalDirectory by lazy {
        createReportDirectoryIfNeeded(sdkDirectory, FATAL_DESTINATION_FILE_PATH)
    }
    private val nonFatalDirectory by lazy {
        createReportDirectoryIfNeeded(sdkDirectory, NON_FATAL_DESTINATION_FILE_PATH)
    }

    override fun persistFatalIssue(
        terminationTimeStampInMilli: Long,
        data: ByteArray,
        reportType: Byte,
    ) {
        writeReportToDisk(
            terminationTimeStampInMilli,
            data,
            reportType,
            fatalDirectory.path,
        )
    }

    override fun persistNonFatalIssue(
        terminationTimeStampInMilli: Long,
        data: ByteArray,
        reportType: Byte,
    ) {
        writeReportToDisk(
            terminationTimeStampInMilli,
            data,
            reportType,
            nonFatalDirectory.path,
        )
    }

    override fun generateFatalIssueFilePath(): String = fatalDirectory.path + "/${UUID.randomUUID()}.cap"

    private fun writeReportToDisk(
        terminationTimeStampInMilli: Long,
        data: ByteArray,
        reportType: Byte,
        destinationPath: String,
    ) {
        val fileName =
            "${terminationTimeStampInMilli}_${mapToReadableType(reportType)}_${UUID.randomUUID()}.cap"
        val outputFile = File(destinationPath, fileName)
        outputFile.writeBytes(data)
    }

    private fun createReportDirectoryIfNeeded(
        sdkDirectory: String,
        destinationDirectory: String,
    ) = File(sdkDirectory, destinationDirectory).apply { if (!exists()) mkdirs() }

    private fun mapToReadableType(reportType: Byte): String =
        when (reportType) {
            ReportType.AppNotResponding -> "anr"
            ReportType.JVMCrash -> "crash"
            ReportType.NativeCrash -> "native_crash"
            ReportType.JavaScriptNonFatalError -> "java_script_non_fatal_error"
            ReportType.JavaScriptFatalError -> "java_script_fatal_error"
            else -> "unknown"
        }

    private companion object {
        private const val FATAL_DESTINATION_FILE_PATH = "/reports/new"

        private const val NON_FATAL_DESTINATION_FILE_PATH = "/reports/watcher/current_session"
    }
}
