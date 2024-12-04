// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.okhttp

import com.android.build.api.instrumentation.ClassContext
import io.bitdrift.capture.SpanAddingClassVisitorFactory
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