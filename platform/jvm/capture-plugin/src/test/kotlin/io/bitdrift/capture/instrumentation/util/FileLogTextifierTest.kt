// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

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

import java.io.File
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

class FileLogTextifierTest {

    class Fixture {

        fun getSut(tmpFile: File) =
            FileLogTextifier(
                Opcodes.ASM7,
                tmpFile,
                "SomeMethod",
                "(Ljava/lang/Throwable;)V"
            )

        fun visitMethodInstructions(sut: FileLogTextifier) {
            sut.visitVarInsn(Opcodes.ASTORE, 0)
            sut.visitLabel(Label())
            sut.visitLdcInsn("db")
        }
    }

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val fixture = Fixture()

    @Test
    fun `prints methodName on ccreation`() {
        fixture.getSut(tmpDir.newFile("instrumentation.log"))

        val file = File(tmpDir.root, "instrumentation.log")
        assertEquals(
            file.readText(),
            "function SomeMethod (Ljava/lang/Throwable;)V\n"
        )
    }

    @Test
    fun `visitMethodEnd flushes output to file if hasn't thrown`() {
        val sut = fixture.getSut(tmpDir.newFile("instrumentation.log"))
        fixture.visitMethodInstructions(sut)
        sut.visitMethodEnd()

        val file = File(tmpDir.root, "instrumentation.log")
        assertEquals(
            file.readText(),
            """
            |function SomeMethod (Ljava/lang/Throwable;)V
            |    ASTORE 0
            |   L0
            |    LDC "db"
            |
            |
            """.trimMargin()
        )
    }

    @Test
    fun `visitMethodEnd does nothing if has thrown`() {
        val sut = fixture.getSut(tmpDir.newFile("instrumentation.log"))
        sut.handle(RuntimeException())
        fixture.visitMethodInstructions(sut)
        sut.visitMethodEnd()

        val file = File(tmpDir.root, "instrumentation.log")
        // sut.handle will add one more newline to the end of file, but actual visited instructions
        // will not be flushed to file
        assertEquals(
            file.readText(),
            """
            |function SomeMethod (Ljava/lang/Throwable;)V
            |
            |
            """.trimMargin()
        )
    }

    @Test
    fun `handle exception flushes output to file`() {
        val sut = fixture.getSut(tmpDir.newFile("instrumentation.log"))
        fixture.visitMethodInstructions(sut)
        sut.handle(RuntimeException())

        val file = File(tmpDir.root, "instrumentation.log")
        assertEquals(
            file.readText(),
            """
            |function SomeMethod (Ljava/lang/Throwable;)V
            |    ASTORE 0
            |   L0
            |    LDC "db"
            |
            |
            """.trimMargin()
        )
    }
}
