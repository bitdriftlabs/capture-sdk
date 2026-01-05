// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Error
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ErrorRelation
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.SDKInfo
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ThreadDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

class IssueReporterStorageTest {
    private lateinit var storage: IssueReporterStorage
    private lateinit var dir: Path

    @Before
    fun setUp() {
        dir = createTempDirectory()
        storage = IssueReporterStorage(dir.pathString)
    }

    @OptIn(ExperimentalPathApi::class)
    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun persistFatalIssue_whenAnr_shouldAddTypeToFileName() {
        assertFileWithExpectedType(
            reportType = ReportType.AppNotResponding,
            "anr",
        )
    }

    @Test
    fun persistFatalIssue_whenJvmCrash_shouldAddTypeToFileName() {
        assertFileWithExpectedType(
            reportType = ReportType.JVMCrash,
            "crash",
        )
    }

    @Test
    fun persistFatalIssue_whenNativeCrash_shouldAddTypeToFileName() {
        assertFileWithExpectedType(
            reportType = ReportType.NativeCrash,
            "native_crash",
        )
    }

    @Test
    fun persistFatalIssue_whenFatalJsError_shouldAddTypeToFileName() {
        assertFileWithExpectedType(
            reportType = ReportType.JavaScriptFatalError,
            "java_script_fatal_error",
        )
    }

    @Test
    fun persistNonFatalIssue_whenNonFatalJsError_shouldAddTypeToFileName() {
        assertFileWithExpectedType(
            reportType = ReportType.JavaScriptNonFatalError,
            "java_script_non_fatal_error",
        )
    }

    @Test
    fun persistNonFatalIssue_whenStrictMode_shouldAddTypeToFileName() {
        assertFileWithExpectedType(
            reportType = ReportType.StrictModeViolation,
            "strict_mode_violation",
        )
    }

    @Test
    fun parseBasicFormat() {
        val builder = FlatBufferBuilder()
        val reportType = ReportType.StrictModeViolation
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
                reportType,
                0,
                0,
                Report.createErrorsVector(builder, errors.toIntArray()),
                ThreadDetails.createThreadDetails(builder, 1u, 0),
                0,
                0,
                0,
            )
        builder.finish(report)
        val timestamp = 1744287332021
        storage.persistFatalIssue(timestamp, builder.sizedByteArray(), reportType)

        val reportsDir = dir.resolve("reports/new").toFile()
        val files = reportsDir.listFiles()
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

    private fun assertFileWithExpectedType(
        reportType: Byte,
        expectedTypeInFileName: String,
    ) {
        val terminationTimeStampInMilli = System.currentTimeMillis()
        val reportData = byteArrayOf()

        storage.persistFatalIssue(
            terminationTimeStampInMilli,
            reportData,
            reportType,
        )

        val reportsDir = dir.resolve("reports/new").toFile()
        val generatedFile = reportsDir.listFiles()?.first()
        assertThat(generatedFile).isNotNull()
        generatedFile?.let {
            assertThat(it.name.contains(expectedTypeInFileName)).isTrue()
        }
    }
}
