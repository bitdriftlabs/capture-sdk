// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.timber

import com.google.common.truth.Truth.assertThat
import io.bitdrift.capture.Capture
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.fieldsOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import timber.log.Timber
import java.io.IOException

class CaptureTreeTest {
    private val mockLogger: IInternalLogger = mock<IInternalLogger>()
    private val captureTree = CaptureTree()
    private val message = "my_message"

    private lateinit var captureMock: MockedStatic<Capture>

    @Before
    fun setUp() {
        Timber.uprootAll()
        captureMock = Mockito.mockStatic(Capture::class.java)
        captureMock.`when`<Any> { Capture.logger() }.thenReturn(mockLogger)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        captureMock.close()
    }

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
        verify(mockLogger).log(eq(LogLevel.WARNING), any<ArrayFields>(), anyOrNull(), argCaptor.capture())
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
        verify(mockLogger).log(eq(LogLevel.INFO), any<ArrayFields>(), anyOrNull(), argCaptor.capture())
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
        verify(mockLogger).log(eq(LogLevel.DEBUG), any<ArrayFields>(), anyOrNull(), argCaptor.capture())
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
        verify(mockLogger).log(eq(LogLevel.TRACE), any<ArrayFields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }

    @Test
    fun `tree logs assert as critical message`() {
        // ARRANGE

        // ACT
        Timber.plant(captureTree)
        Timber.wtf(message)

        // ASSERT
        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(eq(LogLevel.CRITICAL), any<ArrayFields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }

    @Test
    fun `tree uses the current logger instance`() {
       captureMock.`when`<Any> { Capture.logger() }.thenReturn(null, mockLogger)

        Timber.plant(captureTree)
        Timber.i("logged before a logger exists")
        Timber.i(message)

        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(eq(LogLevel.INFO), any<ArrayFields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }

    @Test
    fun `tree logs unrecognized priority as debug message`() {
        // ARRANGE
        // Timber allows an arbitrary priority. Every android.util.Log constant now maps
        // explicitly, so an out-of-range value is what reaches the default branch.
        val unrecognizedPriority = 99

        // ACT
        Timber.plant(captureTree)
        Timber.log(unrecognizedPriority, message)

        // ASSERT
        val argCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(eq(LogLevel.DEBUG), any<ArrayFields>(), anyOrNull(), argCaptor.capture())
        assertThat(argCaptor.firstValue()).isEqualTo(message)
    }
}
