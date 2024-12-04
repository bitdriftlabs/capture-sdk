// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation

import io.bitdrift.capture.SpanAddingClassVisitorFactory
import io.bitdrift.capture.instrumentation.util.CatchingMethodVisitor
import io.bitdrift.capture.instrumentation.util.ExceptionHandler
import io.bitdrift.capture.instrumentation.util.FileLogTextifier
import java.io.File
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.util.TraceMethodVisitor

@Suppress("UnstableApiUsage")
class CommonClassVisitor(
        apiVersion: Int,
        classVisitor: ClassVisitor,
        private val className: String,
        private val methodInstrumentables: List<MethodInstrumentable>,
        private val parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
) : ClassVisitor(apiVersion, classVisitor) {

    private lateinit var log: File

    init {
        // to avoid file creation in case the debug mode is not set
        if (parameters.debug.get()) {

            // create log dir.
            val logDir = parameters.tmpDir.get()
            logDir.mkdirs()

            // delete and recreate file
            log = File(parameters.tmpDir.get(), "$className-instrumentation.log")
            if (log.exists()) {
                log.delete()
            }
            log.createNewFile()
        }
    }

    override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
    ): MethodVisitor {
        var mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        val methodContext = MethodContext(access, name, descriptor, signature, exceptions?.toList())
        val instrumentable = methodInstrumentables.find { it.isInstrumentable(methodContext) }

        var textifier: ExceptionHandler? = null
        if (parameters.debug.get() && instrumentable != null) {
            textifier = FileLogTextifier(api, log, name, descriptor)
            mv = TraceMethodVisitor(mv, textifier)
        }

        val instrumentableVisitor = instrumentable?.getVisitor(methodContext, api, mv, parameters)
        return if (instrumentableVisitor != null) {
            CatchingMethodVisitor(
                    api,
                    instrumentableVisitor,
                    className,
                    methodContext,
                    textifier
            )
        } else {
            mv
        }
    }
}