package io.bitdrift.capture

import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.FatalIssueType
import io.bitdrift.capture.reports.binformat.v1.Report
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
import io.bitdrift.capture.reports.Exception as Error

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
        val report =
            FatalIssueReport(
                FatalIssueType.JVM_CRASH,
                listOf(
                    Error("BadDuck", "failed to create bird property"),
                    Error("BirdGenerator", "invalid configuration"),
                ),
            )
        val timestamp = 1744287332021
        storage.persistFatalIssue(timestamp, report)

        val files = dir.toFile().listFiles()
        assertThat(files?.size).isEqualTo(1)

        val datafile = files!![0]
        val data = ByteBuffer.wrap(datafile.readBytes())
        val parsed = Report.getRootAsReport(data)

        assertThat(parsed.errors(0)!!.name).isEqualTo("BadDuck")
        assertThat(parsed.errors(0)!!.reason).isEqualTo("failed to create bird property")
        assertThat(parsed.errors(1)!!.name).isEqualTo("BirdGenerator")
        assertThat(parsed.errors(1)!!.reason).isEqualTo("invalid configuration")
    }
}
