
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

import com.android.build.api.instrumentation.ClassContext
import io.sentry.android.gradle.instrumentation.fakes.TestClassContext
import io.sentry.android.gradle.instrumentation.fakes.TestClassData
import io.bitdrift.capture.instrumentation.fakes.TestSpanAddingParameters
import java.io.File
import kotlin.test.assertTrue
import org.junit.Test
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class ChainedInstrumentableTest {
    class Fixture {
        fun getSut(
            originalVisitor: ClassVisitor,
            instrumentables: List<ClassInstrumentable> = emptyList()
        ): ClassVisitor {
            return ChainedInstrumentable(instrumentables).getVisitor(
                TestClassContext(TestClassData("RandomClass")),
                Opcodes.ASM7,
                originalVisitor,
                TestSpanAddingParameters(inMemoryDir = File(""))
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when empty instrumentables list returns original visitor`() {
        val sut = fixture.getSut(OriginalVisitor())

        assertTrue { sut is OriginalVisitor }
    }

    @Test
    fun `when no isInstrumentable found returns original visitor`() {
        val sut = fixture.getSut(
            OriginalVisitor(),
            listOf(
                FirstInstrumentable(isInstrumentable = false),
                SecondInstrumentable(isInstrumentable = false)
            )
        )

        assertTrue { sut is OriginalVisitor }
    }

    @Test
    fun `skip non-instrumentables in the chain`() {
        val sut = fixture.getSut(
            OriginalVisitor(),
            listOf(
                FirstInstrumentable(isInstrumentable = false),
                SecondInstrumentable(isInstrumentable = true)
            )
        )

        assertTrue {
            sut is SecondInstrumentable.SecondVisitor && sut.prevVisitor is OriginalVisitor
        }
    }

    @Test
    fun `all instrumentables`() {
        val sut =
            fixture.getSut(OriginalVisitor(), listOf(FirstInstrumentable(), SecondInstrumentable()))

        assertTrue {
            sut is SecondInstrumentable.SecondVisitor &&
                sut.prevVisitor is FirstInstrumentable.FirstVisitor &&
                (sut.prevVisitor as FirstInstrumentable.FirstVisitor).prevVisitor is OriginalVisitor
        }
    }
}

class OriginalVisitor : ClassVisitor(Opcodes.ASM7)

class FirstInstrumentable(val isInstrumentable: Boolean = true) : ClassInstrumentable {
    class FirstVisitor(prevVisitor: ClassVisitor) : ClassVisitor(Opcodes.ASM7, prevVisitor) {
        val prevVisitor get() = cv
    }

    override fun getVisitor(
        instrumentableContext: ClassContext,
        apiVersion: Int,
        originalVisitor: ClassVisitor,
        parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): ClassVisitor = FirstVisitor(originalVisitor)

    override fun isInstrumentable(data: ClassContext): Boolean = isInstrumentable
}

class SecondInstrumentable(val isInstrumentable: Boolean = true) : ClassInstrumentable {
    class SecondVisitor(prevVisitor: ClassVisitor) : ClassVisitor(Opcodes.ASM7, prevVisitor) {
        val prevVisitor get() = cv
    }

    override fun getVisitor(
        instrumentableContext: ClassContext,
        apiVersion: Int,
        originalVisitor: ClassVisitor,
        parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): ClassVisitor = SecondVisitor(originalVisitor)

    override fun isInstrumentable(data: ClassContext): Boolean = isInstrumentable
}
