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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class CaptureTokioThreadTest {
    @Test
    fun `custom field getter runs on the Capture background worker`() {
        // Log admission captures dynamic fields on Capture's producer worker so they describe the
        // state when the log is emitted, rather than later on the Tokio runtime thread.
        val latch = CountDownLatch(1)
        val threadName = AtomicReference<String?>(null)

        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
            customFieldGetters =
                listOf(
                    {
                        threadName.set(Thread.currentThread().name)
                        latch.countDown()
                        mapOf()
                    },
                ),
            bridge = CaptureJniLibrary,
        )

        Logger.logInfo { "Test log message" }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(threadName.get()).isEqualTo("io.bitdrift.capture.background-thread-worker")
    }
}
