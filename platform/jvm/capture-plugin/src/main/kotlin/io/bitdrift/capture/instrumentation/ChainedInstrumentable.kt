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

@file:Suppress("UnstableApiUsage")

package io.bitdrift.capture.instrumentation

import com.android.build.api.instrumentation.ClassContext
import org.objectweb.asm.ClassVisitor
import java.util.LinkedList

class ChainedInstrumentable(
    private val instrumentables: List<ClassInstrumentable> = emptyList(),
) : ClassInstrumentable {
    override fun getVisitor(
        instrumentableContext: ClassContext,
        apiVersion: Int,
        originalVisitor: ClassVisitor,
        parameters: SpanAddingClassVisitorFactory.SpanAddingParameters,
    ): ClassVisitor {
        // build a chain of visitors in order they are provided
        val queue = LinkedList(instrumentables)
        var prevVisitor = originalVisitor
        var visitor: ClassVisitor? = null
        while (queue.isNotEmpty()) {
            val instrumentable = queue.poll()

            visitor =
                if (instrumentable.isInstrumentable(instrumentableContext)) {
                    instrumentable.getVisitor(
                        instrumentableContext,
                        apiVersion,
                        prevVisitor,
                        parameters,
                    )
                } else {
                    prevVisitor
                }
            prevVisitor = visitor
        }
        return visitor ?: originalVisitor
    }

    override fun isInstrumentable(data: ClassContext): Boolean = instrumentables.any { it.isInstrumentable(data) }

    override fun toString(): String =
        "ChainedInstrumentable(instrumentables=" +
            "${instrumentables.joinToString(", ") { it.javaClass.simpleName }})"
}
