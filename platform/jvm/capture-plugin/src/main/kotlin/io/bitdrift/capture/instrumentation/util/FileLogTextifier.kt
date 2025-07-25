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

import org.objectweb.asm.util.Textifier
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

class FileLogTextifier(
    apiVersion: Int,
    log: File,
    methodName: String?,
    methodDescriptor: String?,
) : Textifier(apiVersion),
    ExceptionHandler {
    private var hasThrown = false

    private val fileOutputStream =
        FileOutputStream(log, true).apply {
            write("function $methodName $methodDescriptor".toByteArray())
            write("\n".toByteArray())
        }

    override fun visitMethodEnd() {
        if (!hasThrown) {
            flushPrinter()
        }
    }

    override fun handle(exception: Throwable) {
        hasThrown = true
        flushPrinter()
    }

    private fun flushPrinter() {
        val printWriter = PrintWriter(fileOutputStream)
        print(printWriter)
        printWriter.flush()
        // ASM textifier uses plain "\n" chars, so do we. As it's only for debug and dev purpose
        // it doesn't matter to the end user
        fileOutputStream.write("\n".toByteArray())
        fileOutputStream.close()
    }
}
