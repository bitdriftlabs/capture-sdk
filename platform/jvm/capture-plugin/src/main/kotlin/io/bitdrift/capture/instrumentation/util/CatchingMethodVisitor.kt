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
    private val logger: Logger = CapturePlugin.logger,
) : MethodVisitor(apiVersion, prevVisitor) {
    override fun visitMaxs(
        maxStack: Int,
        maxLocals: Int,
    ) {
        try {
            super.visitMaxs(maxStack, maxLocals)
        } catch (e: Throwable) {
            exceptionHandler?.handle(e)
            logger.error(
                """
                Error while instrumenting $className.${methodContext.name} ${methodContext.descriptor}.
                Please report this issue at https://github.com/bitdriftlabs/capture-sdk/issues
                """.trimIndent(),
                e,
            )
            throw e
        }
    }
}
