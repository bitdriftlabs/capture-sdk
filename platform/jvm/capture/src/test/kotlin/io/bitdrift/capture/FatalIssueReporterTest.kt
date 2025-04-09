// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.fakes.FakeJvmException
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.reports.FatalIssueMechanism
import io.bitdrift.capture.reports.FatalIssueReporter
import io.bitdrift.capture.reports.FatalIssueReporter.Companion.buildFieldsMap
import io.bitdrift.capture.reports.FatalIssueReporter.Companion.getDuration
import io.bitdrift.capture.reports.FatalIssueReporterState
import io.bitdrift.capture.reports.FatalIssueReporterStatus
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class FatalIssueReporterTest {
    private lateinit var fatalIssueReporter: FatalIssueReporter
    private lateinit var reportsDir: File
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = mock()

    @Before
    fun setup() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        reportsDir = File(APP_CONTEXT.filesDir, "bitdrift_capture/reports/")
        fatalIssueReporter = buildReporter(Mocks.sameThreadHandler)
    }

    @Test
    fun initialize_whenIntegrationMechanismAndMissingConfigFile_shouldReportMissingConfigState() {
        prepareFileDirectories(doesReportsDirectoryExist = false)

        fatalIssueReporter.initialize(fatalIssueMechanism = FatalIssueMechanism.Integration)

        fatalIssueReporter
            .fatalIssueReporterStatus
            .assert(FatalIssueReporterState.Initialized.MissingConfigFile::class.java)
    }

    @Test
    fun initialize_whenIntegrationMechanismAndValidConfigFileAndNotReports_shouldReportWithoutPriorPriorState() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "$SOURCE_PATH,json",
            crashDirectoryPresent = true,
            crashFilePresent = false,
        )

        fatalIssueReporter.initialize(fatalIssueMechanism = FatalIssueMechanism.Integration)

        fatalIssueReporter.fatalIssueReporterStatus.assert(
            FatalIssueReporterState.Initialized.WithoutPriorFatalIssue::class.java,
        )
    }

    @Test
    fun initialize_whenIntegrationMechanismAndWithoutSdkCrashDirectory_shouldReportInvalidCrashConfigDirectory() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "$SOURCE_PATH,json",
            crashDirectoryPresent = false,
            crashFilePresent = false,
        )

        fatalIssueReporter.initialize(fatalIssueMechanism = FatalIssueMechanism.Integration)

        fatalIssueReporter.fatalIssueReporterStatus.assert(
            FatalIssueReporterState.Initialized.InvalidCrashConfigDirectory::class.java,
        )
    }

    @Test
    fun initialize_whenIntegrationMechanismAndValidConfigFileAndReports_shouldReportSentPriorReport() {
        assertFileSent("$SOURCE_PATH,json")
    }

    @Test
    fun initialize_whenIntegrationMechanismAndConfigWithSpacesAndReports_shouldReportSentPriorReport() {
        assertFileSent(" $SOURCE_PATH , json       ")
    }

    @Test
    fun initialize_whenCustomConfigAndInValidExtensionConfigAndReports_shouldReportPriorPrior() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "$SOURCE_PATH,yaml",
            crashFilePresent = true,
        )

        fatalIssueReporter.initialize(fatalIssueMechanism = FatalIssueMechanism.Integration)

        fatalIssueReporter.fatalIssueReporterStatus.assert(
            FatalIssueReporterState.Initialized.WithoutPriorFatalIssue::class.java,
        )
    }

    @Test
    fun initialize_whenIntegrationMechanismAndMalformedConfigFileAndPriorReport_shouldReportMalformedConfigFiles() {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = "/data/crashdemo/etc",
            crashFilePresent = true,
        )

        fatalIssueReporter.initialize(fatalIssueMechanism = FatalIssueMechanism.Integration)

        fatalIssueReporter.fatalIssueReporterStatus.assert(
            FatalIssueReporterState.Initialized.MalformedConfigFile::class.java,
        )
    }

    @Test
    fun initialize_whenBuiltInMechanism_shouldInitCrashHandlerAndFetchAppExitReason() {
        fatalIssueReporter.initialize(fatalIssueMechanism = FatalIssueMechanism.BuiltIn)

        verify(captureUncaughtExceptionHandler).install(eq(fatalIssueReporter))
        verify(latestAppExitInfoProvider).get(any())
        fatalIssueReporter.fatalIssueReporterStatus.assert(
            FatalIssueReporterState.BuiltInModeInitialized::class.java,
        )
    }

    @Test
    fun initialize_whenBuiltInMechanismAndArtificialError_shouldReportFailedInitState() {
        val mainThreadHandlerWithException: MainThreadHandler =
            mock {
                on { run(any()) } doAnswer { throw FakeJvmException() }
                on { runAndReturnResult<Any>(any()) } doAnswer { throw FakeJvmException() }
            }
        val fatalIssueReporter = buildReporter(mainThreadHandlerWithException)

        fatalIssueReporter.initialize(FatalIssueMechanism.BuiltIn)

        verify(captureUncaughtExceptionHandler, never()).install(any())
        verify(captureUncaughtExceptionHandler).uninstall()
        verify(latestAppExitInfoProvider, never()).get(any())
        fatalIssueReporter.fatalIssueReporterStatus.assert(
            FatalIssueReporterState.Initialized.ProcessingFailure::class.java,
        )
    }

    private fun FatalIssueReporterStatus.assert(
        expectedType: Class<*>,
        crashFileExist: Boolean = false,
    ) {
        assertThat(state).isInstanceOf(expectedType)
        val expectedMap: Map<String, FieldValue> =
            buildMap {
                put("_fatal_issue_reporting_duration_ms", getDuration().toFieldValue())
                put("_fatal_issue_reporting_state", state.readableType.toFieldValue())
            }
        if (expectedType == FatalIssueReporterState.Initialized.ProcessingFailure::class.java) {
            assertThat(duration).isNull()
        } else {
            assertThat(duration).isNotNull()
        }
        assertThat(buildFieldsMap()).isEqualTo(expectedMap)
        assertCrashFile(crashFileExist)
    }

    private fun prepareFileDirectories(
        doesReportsDirectoryExist: Boolean,
        bitdriftConfigContent: String? = null,
        crashDirectoryPresent: Boolean = true,
        crashFilePresent: Boolean = false,
    ) {
        if (doesReportsDirectoryExist) {
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }
            val reportFile = File(reportsDir, "config")
            bitdriftConfigContent?.let {
                reportFile.writeText(it)
            }
        }

        var sourceCrashDirectory: File? = null
        if (crashDirectoryPresent) {
            bitdriftConfigContent?.let {
                if (!it.contains(",")) return
                val sourcePath = it.split(",")[0].trim()
                sourceCrashDirectory =
                    File(sourcePath.replace("{cache_dir}", APP_CONTEXT.cacheDir.absolutePath))
                if (sourceCrashDirectory?.exists() == false) {
                    sourceCrashDirectory?.mkdirs()
                }
            }

            if (crashFilePresent) {
                sourceCrashDirectory?.let {
                    createCrashFile(it, "first_crash_info.json")
                    createCrashFile(it, "latest_crash_info.json")
                }
            }
        }
    }

    private fun createCrashFile(
        sourceFileDir: File,
        fileName: String,
    ) {
        val sourceFile = File(sourceFileDir, fileName)
        sourceFile.createNewFile()
    }

    private fun assertCrashFile(crashFileExist: Boolean) {
        val crashFile = File(reportsDir, "/new/latest_crash_info.json")
        assertThat(crashFile.exists()).isEqualTo(crashFileExist)
    }

    private fun buildReporter(mainThreadHandler: MainThreadHandler): FatalIssueReporter =
        FatalIssueReporter(
            APP_CONTEXT,
            mainThreadHandler,
            latestAppExitInfoProvider,
            captureUncaughtExceptionHandler,
        )

    private fun assertFileSent(bitdriftConfigContent: String) {
        prepareFileDirectories(
            doesReportsDirectoryExist = true,
            bitdriftConfigContent = bitdriftConfigContent,
            crashFilePresent = true,
        )

        fatalIssueReporter.initialize(fatalIssueMechanism = FatalIssueMechanism.Integration)

        fatalIssueReporter.fatalIssueReporterStatus.assert(
            FatalIssueReporterState.Initialized.FatalIssueReportSent::class.java,
        )
    }

    private companion object {
        private const val SOURCE_PATH = "{cache_dir}/my fake path/acme"
    }
}
