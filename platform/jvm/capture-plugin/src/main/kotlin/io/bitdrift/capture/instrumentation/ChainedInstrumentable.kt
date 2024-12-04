// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("UnstableApiUsage")

package io.bitdrift.capture

import com.android.build.api.instrumentation.ClassContext
import io.bitdrift.capture.instrumentation.ClassInstrumentable
import java.util.LinkedList
import org.objectweb.asm.ClassVisitor

class ChainedInstrumentable(
        private val instrumentables: List<ClassInstrumentable> = emptyList()
) : ClassInstrumentable {

    override fun getVisitor(
            instrumentableContext: ClassContext,
            apiVersion: Int,
            originalVisitor: ClassVisitor,
            parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): ClassVisitor {
        // build a chain of visitors in order they are provided
        val queue = LinkedList(instrumentables)
        var prevVisitor = originalVisitor
        var visitor: ClassVisitor? = null
        while (queue.isNotEmpty()) {
            val instrumentable = queue.poll()

            visitor = if (instrumentable.isInstrumentable(instrumentableContext)) {
                instrumentable.getVisitor(
                        instrumentableContext,
                        apiVersion,
                        prevVisitor,
                        parameters
                )
            } else {
                prevVisitor
            }
            prevVisitor = visitor
        }
        return visitor ?: originalVisitor
    }

    override fun isInstrumentable(data: ClassContext): Boolean =
            instrumentables.any { it.isInstrumentable(data) }

    override fun toString(): String {
        return "ChainedInstrumentable(instrumentables=" +
                "${instrumentables.joinToString(", ") { it.javaClass.simpleName }})"
    }
}