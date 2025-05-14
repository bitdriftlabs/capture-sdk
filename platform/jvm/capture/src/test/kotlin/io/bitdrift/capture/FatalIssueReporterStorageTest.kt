// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.reports.binformat.v1.Error
import io.bitdrift.capture.reports.binformat.v1.ErrorRelation
import io.bitdrift.capture.reports.binformat.v1.Report
import io.bitdrift.capture.reports.binformat.v1.ReportType
import io.bitdrift.capture.reports.binformat.v1.SDKInfo
import io.bitdrift.capture.reports.binformat.v1.ThreadDetails
import io.bitdrift.capture.reports.persistence.FatalIssueReporterStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

class FatalIssueReporterStorageTest {
    private lateinit var storage: FatalIssueReporterStorage
    private lateinit var dir: Path

    @Before
    fun setUp() {
        dir = createTempDirectory()
        storage = FatalIssueReporterStorage(dir.toFile())
    }

    @OptIn(ExperimentalPathApi::class)
    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun parseBasicFormat() {
        val builder = FlatBufferBuilder()
        val errors =
            listOf(
                Error.createError(
                    builder,
                    builder.createString("BadDuck"),
                    builder.createString("failed to create bird property"),
                    0,
                    ErrorRelation.CausedBy,
                ),
                Error.createError(
                    builder,
                    builder.createString("BirdGenerator"),
                    builder.createString("invalid configuration"),
                    0,
                    ErrorRelation.CausedBy,
                ),
            )
        val report =
            Report.createReport(
                builder,
                SDKInfo.createSDKInfo(builder, builder.createString("com.example.some-sdk"), builder.createString("9.7.30")),
                ReportType.StrictModeViolation,
                0,
                0,
                Report.createErrorsVector(builder, errors.toIntArray()),
                ThreadDetails.createThreadDetails(builder, 1u, 0),
                0,
            )
        builder.finish(report)
        val timestamp = 1744287332021
        storage.persistFatalIssue(timestamp, builder.sizedByteArray())

        val files = dir.toFile().listFiles()
        assertThat(files?.size).isEqualTo(1)

        assertThat(files).isNotNull()
        assertThat(files!!.size).isNotEqualTo(0)
        val datafile = files[0]
        val data = ByteBuffer.wrap(datafile.readBytes())
        val parsed = Report.getRootAsReport(data)

        assertThat(parsed.sdk!!.id).isEqualTo("com.example.some-sdk")
        assertThat(parsed.sdk!!.version).isEqualTo("9.7.30")
        assertThat(parsed.type).isEqualTo(ReportType.StrictModeViolation)
        assertThat(parsed.threadDetails!!.count.toInt()).isEqualTo(1)

        assertThat(parsed.errorsLength).isEqualTo(2)
        assertThat(parsed.errors(0)!!.name).isEqualTo("BadDuck")
        assertThat(parsed.errors(0)!!.reason).isEqualTo("failed to create bird property")
        assertThat(parsed.errors(1)!!.name).isEqualTo("BirdGenerator")
        assertThat(parsed.errors(1)!!.reason).isEqualTo("invalid configuration")
    }
}
