// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.strictmode

import android.os.strictmode.Violation
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.fakes.FakeBackgroundThreadHandler
import io.bitdrift.capture.strictmode.IStrictModeReporter
import io.bitdrift.capture.threading.CaptureDispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CaptureStrictModeViolationListenerTest {
    private val mockReporter: IStrictModeReporter = mock()
    private val fakeBackgroundThreadHandler = FakeBackgroundThreadHandler()

    @Before
    fun setUp() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        CaptureDispatchers.setTestExecutorService(MoreExecutors.newDirectExecutorService())
    }

    @OptIn(ExperimentalBitdriftApi::class)
    @Test
    fun onThreadViolations_withMockedReporter_shouldLogReportTwice() {
        val listener =
            CaptureStrictModeViolationListener(
                fakeBackgroundThreadHandler,
                mockReporter,
            )
        val violation = mock<Violation>()

        listener.onThreadViolation(violation)
        listener.onVmViolation(violation)

        verify(mockReporter, times(2)).report(violation)
    }
}
