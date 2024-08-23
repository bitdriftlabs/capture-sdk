// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.session.SessionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CaptureTest {

    // This Test needs to run first since the following tests need to initialize
    // the ContextHolder before they can run.
    @Test
    fun a_configure_skips_logger_creation_when_context_not_initialized() {
        assertThat(Capture.logger()).isNull()

        Logger.configure(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
        )

        assertThat(Capture.logger()).isNull()
    }

    // Accessing fields prior to the configuration of the logger may lead to crash since it can
    // potentially call into a native method that's used to sanitize passed url path.
    @Test
    fun b_does_not_access_fields_if_logger_not_configured() {
        assertThat(Capture.logger()).isNull()

        val requestInfo = HttpRequestInfo("GET", path = HttpUrlPath("/foo/12345"))
        Logger.log(requestInfo)

        val responseInfo = HttpResponseInfo(
            requestInfo,
            response = HttpResponse(
                result = HttpResponse.HttpResult.SUCCESS,
                path = HttpUrlPath("/foo_path/12345"),
            ),
            durationMs = 60L,
        )
        Logger.log(responseInfo)

        assertThat(Capture.logger()).isNull()
    }

    @Test
    fun c_idempotent_configure() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        assertThat(Capture.logger()).isNull()

        Logger.configure(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
        )

        val logger = Capture.logger()
        assertThat(logger).isNotNull()
        assertThat(Logger.deviceId).isNotNull()

        Logger.configure(
            apiKey = "test2",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
        )

        // Calling reconfigure a second time does not change the static logger.
        assertThat(logger).isEqualTo(Capture.logger())
    }
}
