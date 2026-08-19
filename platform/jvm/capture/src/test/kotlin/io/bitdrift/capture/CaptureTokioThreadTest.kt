// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.providers.FieldProvider
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
    fun `tokio thread is correctly named`() {
        // In order to test that the tokio event loop thread is correctly named, we initialize the logger
        // with a field provider that captures the thread name of the calling thread.
        val latch = CountDownLatch(1)
        val threadName = AtomicReference<String?>(null)

        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
            fieldProviders =
                listOf(
                    FieldProvider {
                        threadName.set(Thread.currentThread().name)
                        latch.countDown()
                        mapOf()
                    },
                ),
        )

        val logger = Capture.logger() as LoggerImpl
        try {
            Logger.logInfo { "Test log message" }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(threadName.get()).isEqualTo("bd-tokio")
        } finally {
            // Exercise the worker's teardown path. In jni 0.22 this must detach the persistent
            // event-loop attachment before thread-local storage is destroyed.
            CaptureJniLibrary.shutdown(logger.loggerId)
            CaptureJniLibrary.destroyLogger(logger.loggerId)
            Logger.resetShared()
        }
    }
}
