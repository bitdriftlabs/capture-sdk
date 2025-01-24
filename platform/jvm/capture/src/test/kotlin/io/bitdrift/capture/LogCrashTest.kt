// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.providers.session.SessionStrategy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class LogCrashTest {
    // Run this test in a separate file from the other related tests as it relies on the static handle keeping track
    // of log crashes, so we want to avoid polluting other tests.
    @Test
    fun `crash logs can be recorded before SDK init`() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        CaptureJniLibrary.load()

        val testServerPort = CaptureTestJniLibrary.startTestApiServer(-1)

        assertThat(Capture.logger()).isNull()

        Logger.logCrash()

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Fixed(),
            apiUrl = "http://localhost:$testServerPort".toHttpUrl(),
        )

        val streamId = CaptureTestJniLibrary.awaitNextApiStream()
        CaptureTestJniLibrary.configureAggressiveContinuousUploads(streamId)

        val log = CaptureTestJniLibrary.nextUploadedLog()
        assertThat(log.message).isEqualTo("App Error Reported")
    }
}
