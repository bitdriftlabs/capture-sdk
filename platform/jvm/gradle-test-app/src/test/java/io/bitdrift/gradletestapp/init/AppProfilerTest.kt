// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.annotation.TargetApi
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ProfilingManager
import android.os.ProfilingResult
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.function.Consumer

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@TargetApi(35)
class AppProfilerTest {
    @get:Rule val temporary = TemporaryFolder()

    private val manager: ProfilingManager = mock()
    private val context: Context = mock { on { getSystemService(ProfilingManager::class.java) } doReturn manager }
    private val request = AppProfiler.Request.JavaHeapDump(bufferSizeKb = 65536)

    @Test
    fun javaSnapshotHasABufferButNoDurationAndRetainsItsSavedFile() =
        runTest {
            val capture = async(start = CoroutineStart.UNDISPATCHED) { AppProfiler.capture(context, request) }
            val params = argumentCaptor<Bundle>()
            verify(manager).requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_JAVA_HEAP_DUMP),
                params.capture(),
                anyOrNull(),
                any(),
                any(),
                any(),
            )
            val bufferSizeKey = "KEY_SIZE_KB"
            assertEquals(setOf(bufferSizeKey), params.firstValue.keySet())
            assertEquals(65536, params.firstValue.getInt(bufferSizeKey))
            val file = temporary.newFile()

            deliver(result(path = file.path))

            assertEquals(file.path, (capture.await() as AppProfiler.Result.Saved).path)
            assertTrue(file.exists())
        }

    @Test
    fun cancellationStopsThePlatformAndDeletesALateSavedFile() =
        runTest {
            val capture = async(start = CoroutineStart.UNDISPATCHED) { AppProfiler.capture(context, request) }
            val signal = argumentCaptor<CancellationSignal>()
            verify(manager).requestProfiling(
                eq(ProfilingManager.PROFILING_TYPE_JAVA_HEAP_DUMP),
                any(),
                anyOrNull(),
                signal.capture(),
                any(),
                any(),
            )
            capture.cancel()
            runCurrent()
            assertTrue(signal.firstValue.isCanceled)
            val file = temporary.newFile()

            deliver(result(path = file.path))

            assertFalse(file.exists())
        }

    @Test
    fun cancellationAfterCallbackBeforeResumptionDeletesTheSavedFile() =
        runTest {
            val capture = async(start = CoroutineStart.UNDISPATCHED) { AppProfiler.capture(context, request) }
            val file = temporary.newFile()
            deliver(result(path = file.path))

            capture.cancel()
            runCurrent()

            assertFalse(file.exists())
        }

    @Test
    fun platformFailuresKeepTheirCodesNamesAndMessages() =
        runTest {
            for ((code, name) in listOf(
                ProfilingResult.ERROR_FAILED_RATE_LIMIT_PROCESS to "ERROR_FAILED_RATE_LIMIT_PROCESS",
                ProfilingResult.ERROR_FAILED_NO_DISK_SPACE to "ERROR_FAILED_NO_DISK_SPACE",
                ProfilingResult.ERROR_FAILED_INVALID_REQUEST to "ERROR_FAILED_INVALID_REQUEST",
            )) {
                val capture = async(start = CoroutineStart.UNDISPATCHED) { AppProfiler.capture(context, request) }
                deliver(result(code = code, message = "platform rejected capture"))

                val failure = capture.await() as AppProfiler.Result.Failure
                assertEquals(code, failure.errorCode)
                assertEquals(name, failure.errorName)
                assertEquals("platform rejected capture", failure.message)
                clearInvocations(manager)
            }
        }

    @Test
    fun successWithoutAFileFailsExplicitly() =
        runTest {
            val capture = async(start = CoroutineStart.UNDISPATCHED) { AppProfiler.capture(context, request) }
            deliver(result())

            assertEquals("no profile file produced", (capture.await() as AppProfiler.Result.Failure).message)
        }

    @Test
    @Config(sdk = [34])
    fun unsupportedApiDoesNotRequestAProfile() =
        runTest {
            assertTrue(AppProfiler.capture(context, request) is AppProfiler.Result.Failure)
            verifyNoInteractions(context)
        }

    private fun deliver(result: ProfilingResult) {
        val listener = argumentCaptor<Consumer<ProfilingResult>>()
        verify(manager).requestProfiling(
            eq(ProfilingManager.PROFILING_TYPE_JAVA_HEAP_DUMP),
            any(),
            anyOrNull(),
            any(),
            any(),
            listener.capture(),
        )
        listener.firstValue.accept(result)
    }

    private fun result(
        code: Int = ProfilingResult.ERROR_NONE,
        path: String? = null,
        message: String? = null,
    ): ProfilingResult =
        mock {
            on { errorCode } doReturn code
            on { resultFilePath } doReturn path
            on { errorMessage } doReturn message
        }
}
