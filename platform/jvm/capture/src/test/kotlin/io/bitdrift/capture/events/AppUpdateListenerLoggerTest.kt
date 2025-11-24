// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.ContextHolder
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AppUpdateListenerLoggerTest {
    private val logger: LoggerImpl = mock()
    private val clientAttributes: ClientAttributes = mock()
    private val runtime: Runtime = mock()

    private lateinit var appUpdateLogger: AppUpdateListenerLogger

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initializer = ContextHolder()
        initializer.create(context)

        // Set up a fake sourceDir for the APK size calculation
        context.applicationInfo.sourceDir = "/fake/path/to/app.apk"
    }

    @Test
    fun doesNotLogAppUpdateWhenShouldLogReturnsFalse() {
        whenever(runtime.isEnabled(RuntimeFeature.APP_UPDATE_EVENTS)).thenReturn(true)
        whenever(clientAttributes.appVersion).thenReturn("1.2.3")
        whenever(clientAttributes.appVersionCode).thenReturn(123)
        whenever(logger.shouldLogAppUpdate("1.2.3", 123)).thenReturn(false)

        val executor = Executors.newSingleThreadExecutor()
        appUpdateLogger =
            AppUpdateListenerLogger(
                logger = logger,
                clientAttributes = clientAttributes,
                context = ContextHolder.APP_CONTEXT,
                runtime = runtime,
                executor = executor,
            )

        appUpdateLogger.start()
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)
        verify(logger, never()).logAppUpdate(any(), any(), any(), any())
    }

    @Test
    fun logsAppUpdateWhenShouldLogReturnsTrue() {
        whenever(runtime.isEnabled(RuntimeFeature.APP_UPDATE_EVENTS)).thenReturn(true)
        whenever(clientAttributes.appVersion).thenReturn("1.2.3")
        whenever(clientAttributes.appVersionCode).thenReturn(123)
        whenever(logger.shouldLogAppUpdate("1.2.3", 123)).thenReturn(true)

        val executor = Executors.newSingleThreadExecutor()
        appUpdateLogger =
            AppUpdateListenerLogger(
                logger = logger,
                clientAttributes = clientAttributes,
                context = ContextHolder.APP_CONTEXT,
                runtime = runtime,
                executor = executor,
            )

        appUpdateLogger.start()
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)
        verify(logger).logAppUpdate(eq("1.2.3"), eq(123), any(), any())
    }
}
