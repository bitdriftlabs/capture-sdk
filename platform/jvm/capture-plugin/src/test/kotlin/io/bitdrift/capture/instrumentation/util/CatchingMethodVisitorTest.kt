/**
 * Adapted from https://github.com/getsentry/sentry-android-gradle-plugin/tree/4.14.1
 *
 * MIT License
 *
 * Copyright (c) 2020 Sentry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bitdrift.capture.instrumentation.util

import io.bitdrift.capture.instrumentation.MethodContext
import io.bitdrift.capture.instrumentation.fakes.CapturingTestLogger
import kotlin.test.assertEquals
import org.junit.Test
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class CatchingMethodVisitorTest {

    class Fixture {
        private val throwingVisitor = ThrowingMethodVisitor()
        val handler = CapturingExceptionHandler()
        val logger = CapturingTestLogger()

        private val methodContext =
            MethodContext(Opcodes.ACC_PUBLIC, "someMethod", null, null, null)
        val sut
            get() = CatchingMethodVisitor(
                Opcodes.ASM7,
                throwingVisitor,
                "SomeClass",
                methodContext,
                handler,
                logger
            )
    }

    private val fixture = Fixture()

    @Test
    fun `forwards exception to ExceptionHandler`() {
        try {
            fixture.sut.visitMaxs(0, 0)
        } catch (ignored: Throwable) {
        } finally {
            assertEquals(fixture.handler.capturedException!!.message, "This method throws!")
        }
    }

    @Test(expected = CustomException::class)
    fun `rethrows exception`() {
        fixture.sut.visitMaxs(0, 0)
    }

    @Test
    fun `prints message to log`() {
        try {
            fixture.sut.visitMaxs(0, 0)
        } catch (ignored: Throwable) {
        } finally {
            assertEquals(fixture.logger.capturedThrowable!!.message, "This method throws!")
            assertEquals(
                fixture.logger.capturedMessage,
                """
                Error while instrumenting SomeClass.someMethod null.
                Please report this issue at https://github.com/bitdriftlabs/capture-sdk/issues
                """.trimIndent()
            )
        }
    }
}

class CustomException : RuntimeException("This method throws!")

class ThrowingMethodVisitor : MethodVisitor(Opcodes.ASM7) {

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        throw CustomException()
    }
}

class CapturingExceptionHandler : ExceptionHandler {

    var capturedException: Throwable? = null

    override fun handle(exception: Throwable) {
        capturedException = exception
    }
}
