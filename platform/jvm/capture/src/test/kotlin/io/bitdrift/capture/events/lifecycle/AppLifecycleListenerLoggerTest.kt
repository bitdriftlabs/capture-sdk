// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.Mocks
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.Fields
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.utils.BuildVersionChecker
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import java.util.concurrent.ExecutorService

class AppLifecycleListenerLoggerTest {
    private val logger: LoggerImpl = mock()
    private val processLifecycleOwner: LifecycleOwner = mock()
    private val activityManager: ActivityManager = mock()
    private val runtime: Runtime = mock()
    private val executor: ExecutorService = MoreExecutors.newDirectExecutorService()
    private val handler = Mocks.sameThreadHandler
    private val versionChecker: BuildVersionChecker = mock()

    private lateinit var appLifecycleLogger: AppLifecycleListenerLogger

    @Before
    fun setUp() {
        whenever(runtime.isEnabled(RuntimeFeature.APP_LIFECYCLE_EVENTS)).thenReturn(true)
        whenever(versionChecker.isAtLeast(anyInt())).thenReturn(false)
        appLifecycleLogger =
            AppLifecycleListenerLogger(logger, processLifecycleOwner, activityManager, runtime, executor, handler, versionChecker)
    }

    @Test
    fun testLogsAreFlushedOnStop() {
        // ARRANGE

        // ACT
        appLifecycleLogger.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_STOP)

        // ASSERT
        verify(logger).flush(eq(false))
    }

    @Test
    fun testAppStartInfoFieldsAreSkippedIfNotCreate() {
        // ARRANGE
        whenever(versionChecker.isAtLeast(35)).thenReturn(true)

        // ACT
        appLifecycleLogger.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_START)

        // ASSERT
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.INFO),
            eq(Fields.EMPTY),
            eq(Fields.EMPTY),
            eq(null),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppStart" },
        )
    }

    @Test
    fun testAppStartInfoFieldsAreSkippedIfError() {
        // ARRANGE
        whenever(versionChecker.isAtLeast(35)).thenReturn(true)
        val error = RuntimeException("test exception")
        whenever(activityManager.getHistoricalProcessStartReasons(anyOrNull())).thenThrow(error)

        // ACT
        appLifecycleLogger.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_CREATE)

        // ASSERT
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.INFO),
            eq(Fields.EMPTY),
            eq(Fields.EMPTY),
            eq(null),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppCreate" },
        )
    }

    @Test
    fun testAppStartInfoFieldsAreLogged() {
        // ARRANGE
        val launchTime = 10 * 1_000_000L
        val firstFrameTime = 100 * 1_000_000L
        val expectedTTID = (firstFrameTime - launchTime) / 1_000_000L
        val expectedFields =
            buildMap {
                // custom mocked values
                put("startup_type", "COLD")
                put("startup_state", "FIRST_FRAME_DRAWN")
                put("startup_intent_action", "android.intent.action.MAIN")
                put("startup_time_to_initial_display_ms", expectedTTID.toString())
                // default mock values
                put("startup_launch_mode", "STANDARD")
                put("startup_was_forced_stopped", "false")
                put("startup_reason", "ALARM")
            }.toFields()

        whenever(versionChecker.isAtLeast(35)).thenReturn(true)

        val mockStartInfo = mock<ApplicationStartInfo>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(mockStartInfo.startType).thenReturn(ApplicationStartInfo.START_TYPE_COLD)
        whenever(mockStartInfo.startupState).thenReturn(ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN)
        whenever(mockStartInfo.intent?.action).thenReturn("android.intent.action.MAIN")
        whenever(mockStartInfo.startupTimestamps).thenReturn(
            mapOf(
                ApplicationStartInfo.START_TIMESTAMP_LAUNCH to launchTime,
                ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME to firstFrameTime,
            ),
        )
        whenever(activityManager.getHistoricalProcessStartReasons(1)).thenReturn(listOf(mockStartInfo))

        // ACT
        appLifecycleLogger.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_CREATE)

        // ASSERT
        verify(logger).log(
            eq(LogType.LIFECYCLE),
            eq(LogLevel.INFO),
            argThat<Fields> { fields ->
                val actualKeys = fields.keys.toSet()
                val expectedKeys = expectedFields.keys.toSet()
                actualKeys.containsAll(expectedKeys)
            },
            eq(Fields.EMPTY),
            eq(null),
            eq(false),
            argThat { i: () -> String -> i.invoke() == "AppCreate" },
        )
    }
}
