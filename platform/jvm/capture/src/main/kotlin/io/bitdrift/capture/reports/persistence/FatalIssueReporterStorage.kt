// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
@file:Suppress("ktlint:standard:no-wildcard-imports")

package io.bitdrift.capture.reports.persistence

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.binformat.v1.*
import java.io.File
import java.util.UUID

/**
 * Persists a [FatalIssueReport] into disk
 */
internal class FatalIssueReporterStorage(
    private val destinationDirectory: File,
) : IFatalIssueReporterStorage {
    override fun persistFatalIssue(
        terminationTimeStampInMilli: Long,
        fatalIssueReport: FatalIssueReport,
    ) {
        val fileName = "${terminationTimeStampInMilli}_${UUID.randomUUID()}.cap"
        val outputFile = File(destinationDirectory, fileName)
        outputFile.writeBytes(serializeReport(fatalIssueReport))
    }

    private fun serializeReport(report: FatalIssueReport): ByteArray {
        val builder = FlatBufferBuilder(2048)
        val sdk =
            SDKInfo.createSDKInfo(
                builder,
                builder.createString("x.x.x"),
                builder.createString("io.bitdrift.capture-android"),
            )

        val errors =
            Report.createErrorsVector(
                builder,
                report.errors
                    .map {
                        Error.createError(
                            builder,
                            builder.createString(it.name),
                            builder.createString(it.reason),
                            Error.createStackTraceVector(builder, intArrayOf()),
                            ErrorRelation.CausedBy,
                        )
                    }.toIntArray(),
            )

        val obj = Report.createReport(builder, sdk, 0, 0, errors, 0, 0)
        builder.finish(obj)

        return builder.sizedByteArray()
    }
}
