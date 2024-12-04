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

package io.bitdrift.capture.instrumentation

import io.bitdrift.capture.instrumentation.util.CatchingMethodVisitor
import io.bitdrift.capture.instrumentation.fakes.TestSpanAddingParameters
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class CommonClassVisitorTest {

    class Fixture {

        fun getSut(tmpDir: File, debug: Boolean = false) =
            CommonClassVisitor(
                Opcodes.ASM7,
                ParentClassVisitor(),
                "SomeClass",
                listOf(TestInstrumentable()),
                TestSpanAddingParameters(debugOutput = debug, inMemoryDir = tmpDir)
            )
    }

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val fixture = Fixture()

    @Test
    fun `when debug - creates a file with class name on init`() {
        fixture.getSut(tmpDir.root, true)

        val file = File(tmpDir.root, "SomeClass-instrumentation.log")
        assertTrue { file.exists() }
    }

    @Test
    fun `when debug and is instrumentable - prepends with TraceMethodVisitor`() {
        val mv = fixture.getSut(tmpDir.root, true)
            .visitMethod(Opcodes.ACC_PUBLIC, "test", null, null, null)

        mv.visitVarInsn(Opcodes.ASTORE, 0)
        mv.visitEnd()

        // we read the file and compare its content to ensure that TraceMethodVisitor was called and
        // wrote the instructions into the file
        val file = File(tmpDir.root, "SomeClass-instrumentation.log")
        assertEquals(
            file.readText(),
            """
            |function test null
            |    ASTORE 0
            |
            |
            """.trimMargin()
        )
    }

    @Test
    fun `when no debug and is instrumentable - skips TraceMethodVisitor`() {
        val mv = fixture.getSut(tmpDir.root, true)
            .visitMethod(Opcodes.ACC_PUBLIC, "other", null, null, null)

        mv.visitVarInsn(Opcodes.ASTORE, 0)
        mv.visitEnd()

        // we read the file and compare its content to ensure that TraceMethodVisitor was skipped
        val file = File(tmpDir.root, "SomeClass-instrumentation.log")
        assertTrue { file.readText().isEmpty() }
    }

    @Test
    fun `when matches method name returns instrumentable visitor wrapped into catching visitor`() {
        val mv =
            fixture.getSut(tmpDir.root).visitMethod(Opcodes.ACC_PUBLIC, "test", null, null, null)

        assertTrue { mv is CatchingMethodVisitor }
    }

    @Test
    fun `when doesn't match method name return original visitor`() {
        val mv =
            fixture.getSut(tmpDir.root).visitMethod(Opcodes.ACC_PUBLIC, "other", null, null, null)

        assertTrue { mv is ParentClassVisitor.ParentMethodVisitor }
    }
}

class ParentClassVisitor : ClassVisitor(Opcodes.ASM7) {

    inner class ParentMethodVisitor : MethodVisitor(Opcodes.ASM7)

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor = ParentMethodVisitor()
}

class TestInstrumentable : MethodInstrumentable {

    inner class TestVisitor(originalVisitor: MethodVisitor) :
        MethodVisitor(Opcodes.ASM7, originalVisitor)

    override val fqName: String get() = "test"

    override fun getVisitor(
        instrumentableContext: MethodContext,
        apiVersion: Int,
        originalVisitor: MethodVisitor,
        parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): MethodVisitor = TestVisitor(originalVisitor)
}
