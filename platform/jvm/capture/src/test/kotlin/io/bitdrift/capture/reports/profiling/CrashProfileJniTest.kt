// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.MockPreferences
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.events.performance.IMemoryMetricsProvider
import io.bitdrift.capture.events.performance.MemoryPressureLevel
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.persistence.IIssueReporterStore
import io.bitdrift.capture.reports.processor.AnrReport
import io.bitdrift.capture.reports.processor.IssueReporterProcessor
import io.bitdrift.capture.reports.processor.JniStreamingReportProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess
import java.nio.ByteBuffer
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashProfileJniTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val attributes = ClientAttributes(context, mock())

    @Before
    fun setUp() {
        CaptureJniLibrary.load()
        ShadowProcess.setPid(12345)
        CrashProfileSource.resetForTest()
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 7)
    }

    @After
    fun tearDown() {
        CrashProfileSource.resetForTest()
    }

    @Test
    fun capturedSourceRoundTripsThroughNativeEncodingAndReportPersistenceForAllApiLevels() {
        val source = checkNotNull(CrashProfileSource.current(context, ::encode))
        val facts = checkNotNull(captured)
        assertThat(CrashProfileSource.current(context) { error("source must be cached") }).isSameAs(source)

        // Exercise the real JNI metadata and persistence boundary for pre-ANR, ANR, and OOM APIs.
        // Robolectric's framework is API 35; no ProfilingManager or service is required.
        listOf(35, 36, 37).forEach { api ->
            withNativeLogger(api) { processor ->
                val report = persist(processor, source, facts.pid)
                assertThat(report.appMetrics!!.processId).isEqualTo(facts.pid.toUInt())
                assertThat(report.source()).isEqualTo(source)
            }
        }
    }

    @Test
    fun nativeReportPersistenceIgnoresMalformedForeignAndDifferentProcessSummaries() {
        val source = checkNotNull(CrashProfileSource.current(context, ::encode))
        val facts = checkNotNull(captured)
        listOf(35, 36, 37).forEach { api ->
            withNativeLogger(api) { processor ->
                listOf(null, byteArrayOf(0x0a, 0x7f), """{"owner":"application"}""".toByteArray()).forEach { summary ->
                    val report = persist(processor, summary, facts.pid)
                    assertThat(report.source()).isEmpty()
                    assertThat(report.appMetrics!!.processId).isEqualTo(facts.pid.toUInt())
                }
                val otherProcess = persist(processor, source, facts.pid + 1)
                assertThat(otherProcess.source()).isEmpty()
                assertThat(otherProcess.appMetrics!!.processId).isEqualTo((facts.pid + 1).toUInt())
            }
        }
    }

    @Test
    fun nativeEncodedFactsSurviveTheRealKotlinOomWriterAndNativePidValidation() {
        val source = checkNotNull(CrashProfileSource.current(context, ::encode))
        CrashProfileSource.oomSource = source
        val storage: IIssueReporterStore = mock()
        val memory: IMemoryMetricsProvider = mock()
        whenever(memory.getCurrentJvmMemoryPressureLevel()).thenReturn(MemoryPressureLevel.Critical)
        val timestamp = 1234567890123L
        val processor =
            IssueReporterProcessor(
                storage,
                attributes,
                mock(),
                DateProvider { Date(timestamp) },
                mock(),
                memory,
                temporary.root.path,
                mock(),
            )

        processor.processJvmCrash(Thread.currentThread(), OutOfMemoryError("heap"), null)

        val bytes = argumentCaptor<ByteArray>()
        verify(storage).persistFatalIssue(eq(timestamp), bytes.capture(), eq(ReportType.JVMCrash))
        val report = Report.getRootAsReport(ByteBuffer.wrap(bytes.firstValue))
        assertThat(report.source()).isEqualTo(source)
        val pid = report.appMetrics!!.processId.toInt()
        assertThat(pid).isEqualTo(checkNotNull(captured).pid)
        withNativeLogger(37) { native ->
            assertThat(persist(native, report.source(), pid).source()).isEqualTo(source)
            assertThat(persist(native, report.source(), pid + 1).source()).isEmpty()
        }
    }

    private var captured: CrashProfileFacts? = null

    private fun encode(facts: CrashProfileFacts): ByteArray? {
        captured = facts
        return CaptureJniLibrary.encodeCrashProfileSource(
            facts.pid,
            facts.bootCount,
            facts.processStartElapsedNs,
            facts.wallNs,
            facts.elapsedNs,
        )
    }

    private fun persist(
        processor: JniStreamingReportProcessor,
        source: ByteArray?,
        pid: Int,
    ): Report {
        val destination = temporary.newFile()
        processor.processAndPersistANR(
            AnrReport(
                stream = null,
                timestampMillis = 1234567890123,
                destinationPath = destination.path,
                tempDirectoryPath = temporary.root.path,
                runningState = "foreground",
                appExitDescription = null,
                memoryPressureLevel = 0,
                isFileSizeOptimizationEnabled = false,
                processId = pid,
                crashProfileSource = source,
            ),
        )
        return Report.getRootAsReport(ByteBuffer.wrap(destination.readBytes()))
    }

    private fun Report.source(): ByteArray = ByteArray(crashProfileSourceLength) { crashProfileSource(it).toByte() }

    private fun withNativeLogger(
        api: Int,
        block: (JniStreamingReportProcessor) -> Unit,
    ) {
        val id =
            CaptureJniLibrary.createLogger(
                sdkDirectory = temporary.newFolder().path,
                apiKey = "test",
                targetDomain = "https://capture.invalid:443",
                initialSessionId = null,
                inactivityTimeoutMilliseconds = -1,
                sessionCallback = null,
                timestampProvider = null,
                customFieldsProvider = null,
                initialOotbFields = emptyArray(),
                resourceUtilizationTarget = mock(),
                sessionReplayTarget = mock(),
                eventsListenerTarget = mock(),
                applicationId = "test",
                applicationVersion = "1",
                osVersion = api.toString(),
                manufacturer = "test",
                model = "test",
                appVersionCode = 1,
                osApiLevel = api,
                architecture = "arm64",
                network = mock(),
                preferences = MockPreferences(),
                errorReporter = mock(),
                startInSleepMode = false,
                issueCallbackConfiguration = null,
                initialFields = emptyArray(),
            )
        assertThat(id).isNotEqualTo(-1)
        try {
            // Never start the logger: persistence needs its static metadata, not network/services.
            block(JniStreamingReportProcessor(id, attributes))
        } finally {
            CaptureJniLibrary.destroyLogger(id)
        }
    }
}
