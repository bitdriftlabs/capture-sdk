// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.timber

import com.google.common.truth.Truth.assertThat
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.providers.Fields
import io.bitdrift.capture.providers.fieldsOf
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import timber.log.Timber
import java.io.IOException

class CaptureTreeTest {
    private val mockLogger: ILogger = mock()
    private val captureTree = CaptureTree(mockLogger)
    private val message = "my_message"

    @Test
    fun `tree logs error with all fields`() {
        // ARRANGE
        val tag = "my_tag"
        val exception = IOException("my_exception")

        // ACT
        Timber.plant(captureTree)
        Timber.tag(tag).e(exception, message)

        // ASSERT
        val expectedFields = fieldsOf(
            "source" to "Timber",
            "tag" to tag,
        )
        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(
            eq(LogLevel.ERROR),
            eq(expectedFields),
            eq(exception),
            argCaptor.capture(),
        )
        assertThat(argCaptor.firstValue()).isEqualTo(message + "\n" + exception.stackTraceToString())
    }

    @Test
    fun `tree logs level warning message`() {
        // ARRANGE

        // ACT
        Timber.plant(captureTree)
        Timber.w(message)

        // ASSERT
        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(eq(LogLevel.WARNING), any<Fields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }

    @Test
    fun `tree logs level info message`() {
        // ARRANGE

        // ACT
        Timber.plant(captureTree)
        Timber.i(message)

        // ASSERT
        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(eq(LogLevel.INFO), any<Fields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }

    @Test
    fun `tree logs level debug message`() {
        // ARRANGE

        // ACT
        Timber.plant(captureTree)
        Timber.d(message)

        // ASSERT
        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(eq(LogLevel.DEBUG), any<Fields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }

    @Test
    fun `tree logs level trace message`() {
        // ARRANGE

        // ACT
        Timber.plant(captureTree)
        Timber.v(message)

        // ASSERT
        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(eq(LogLevel.TRACE), any<Fields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }

    @Test
    fun `tree logs default level debug message`() {
        // ARRANGE

        // ACT
        Timber.plant(captureTree)
        Timber.wtf(message)

        // ASSERT
        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(eq(LogLevel.DEBUG), any<Fields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }
}
