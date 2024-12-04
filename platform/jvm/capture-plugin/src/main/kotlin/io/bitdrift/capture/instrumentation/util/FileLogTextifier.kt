// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.util

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import org.objectweb.asm.util.Textifier

class FileLogTextifier(
        apiVersion: Int,
        log: File,
        methodName: String?,
        methodDescriptor: String?
) : Textifier(apiVersion), ExceptionHandler {

    private var hasThrown = false

    private val fileOutputStream = FileOutputStream(log, true).apply {
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