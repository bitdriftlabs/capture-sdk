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

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class MinifiedClassDetectionTest {

    @Test
    fun `detects minified class names`() {
        val classNames = listOf(
            "l0",
            """a${'$'}a""",
            "ccc017zz",
            """ccc017zz${'$'}a""",
            "aa",
            "aa${'$'}a",
            "ab",
            "aa${'$'}ab",
            "ab${'$'}a"
        )

        classNames.forEach {
            assertTrue(classNameLooksMinified(it, "com/example/$it"), it)
        }
    }

    @Test
    fun `detects minified class names with minified package name`() {
        val classNames = listOf(
            """a${'$'}""",
            "aa"
        )

        classNames.forEach {
            assertTrue(classNameLooksMinified(it, "a/$it"), it)
        }
    }

    @Test
    fun `does not consider non minified classes as minified`() {
        val classNames = listOf(
            "ConstantPoolHelpers",
            "FileUtil",
            """FileUtil${"$"}Inner"""
        )

        classNames.forEach {
            assertFalse(classNameLooksMinified(it, "com/example/$it"), it)
        }
    }

    @Test
    fun `does not consider short class names as minified classes`() {
        val classNames = listOf(
            Pair("Call", "retrofit2/Call"),
            Pair("Call", "okhttp3/Call"),
            Pair("Fill", "androidx/compose/ui/graphics/drawscope/Fill"),
            Pair("Px", "androidx/annotation/Px"),
            Pair("Dp", "androidx/annotation/Dp")
        )

        classNames.forEach { (simpleName, fullName) ->
            assertFalse(classNameLooksMinified(simpleName, fullName))
        }
    }
}
