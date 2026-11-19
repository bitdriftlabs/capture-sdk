// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.providers.session.SessionConfiguration
import io.bitdrift.capture.providers.session.SessionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class CaptureTokioThreadTest {
    @Test
    fun `custom field getter runs inline on the logging thread`() {
        // Startup logs on background threads must not overwrite the test log's observation.
        val getterThread = ThreadLocal<Thread>()

        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
            customFieldGetters =
                listOf(
                    {
                        getterThread.set(Thread.currentThread())
                        mapOf()
                    },
                ),
            bridge = CaptureJniLibrary,
        )

        getterThread.remove()
        Logger.logInfo { "Test log message" }

        assertThat(getterThread.get()).isSameAs(Thread.currentThread())
    }
}
