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

package io.bitdrift.capture.instrumentation.okhttp

import com.android.build.api.instrumentation.ClassContext
import io.bitdrift.capture.instrumentation.SpanAddingClassVisitorFactory
import io.bitdrift.capture.instrumentation.ClassInstrumentable
import io.bitdrift.capture.instrumentation.CommonClassVisitor
import io.bitdrift.capture.instrumentation.MethodContext
import io.bitdrift.capture.instrumentation.MethodInstrumentable
import io.bitdrift.capture.instrumentation.okhttp.visitor.OkHttpEventListenerMethodVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

class OkHttpEventListener(
) : ClassInstrumentable {
    override val fqName: String get() = "okhttp3.OkHttpClient"

    override fun getVisitor(
            instrumentableContext: ClassContext,
            apiVersion: Int,
            originalVisitor: ClassVisitor,
            parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): ClassVisitor = CommonClassVisitor(
            apiVersion = apiVersion,
            classVisitor = originalVisitor,
            className = fqName.substringAfterLast('.'),
            methodInstrumentables = listOf(
                    OkHttpEventListenerMethodInstrumentable(
                    )
            ),
            parameters = parameters
    )
}

class OkHttpEventListenerMethodInstrumentable(
) : MethodInstrumentable {
    override val fqName: String get() = "<init>"

    override fun getVisitor(
            instrumentableContext: MethodContext,
            apiVersion: Int,
            originalVisitor: MethodVisitor,
            parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): MethodVisitor = OkHttpEventListenerMethodVisitor(
            apiVersion = apiVersion,
            originalVisitor = originalVisitor,
            instrumentableContext = instrumentableContext,
    )

    override fun isInstrumentable(data: MethodContext): Boolean {
        return data.name == fqName && data.descriptor == "(Lokhttp3/OkHttpClient\$Builder;)V"
    }
}