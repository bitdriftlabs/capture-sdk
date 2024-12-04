// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.util

import io.bitdrift.capture.CapturePlugin
import io.bitdrift.capture.instrumentation.MethodContext
import org.objectweb.asm.MethodVisitor
import org.slf4j.Logger

interface ExceptionHandler {
    fun handle(exception: Throwable)
}

class CatchingMethodVisitor(
        apiVersion: Int,
        prevVisitor: MethodVisitor,
        private val className: String,
        private val methodContext: MethodContext,
        private val exceptionHandler: ExceptionHandler? = null,
        private val logger: Logger = CapturePlugin.logger
) : MethodVisitor(apiVersion, prevVisitor) {

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        try {
            super.visitMaxs(maxStack, maxLocals)
        } catch (e: Throwable) {
            exceptionHandler?.handle(e)
            logger.error(
                    """
                Error while instrumenting $className.${methodContext.name} ${methodContext.descriptor}.
                Please report this issue at https://github.com/getsentry/sentry-android-gradle-plugin/issues
                """.trimIndent(), e
            )
            throw e
        }
    }
}
