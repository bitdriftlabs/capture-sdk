// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.okhttp.visitor

import io.bitdrift.capture.instrumentation.MethodContext
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter

class OkHttpEventListenerMethodVisitor(
        apiVersion: Int,
        originalVisitor: MethodVisitor,
        instrumentableContext: MethodContext,
) : AdviceAdapter(
        apiVersion,
        originalVisitor,
        instrumentableContext.access,
        instrumentableContext.name,
        instrumentableContext.descriptor
) {

    private val captureOkHttpEventListenerFactory =
            "io/bitdrift/capture/network/okhttp/CaptureOkHttpEventListenerFactory"

    override fun onMethodEnter() {
        super.onMethodEnter()
        // Add the following call at the beginning of the constructor with the Builder parameter:
        // builder.eventListenerFactory(new CaptureOkHttpEventListenerFactory());

        // OkHttpClient.Builder is the parameter, retrieved here
        visitVarInsn(Opcodes.ALOAD, 1)

        // Let's declare the SentryOkHttpEventListener variable
        visitTypeInsn(Opcodes.NEW, captureOkHttpEventListenerFactory)

        // The CaptureOkHttpEventListenerFactory constructor, which is called later, will consume the
        //  element without pushing anything back to the stack (<init> returns void).
        // Dup will give a reference to the CaptureOkHttpEventListenerFactory after the constructor call
        visitInsn(Opcodes.DUP)
//
//        // Puts parameter OkHttpClient.Builder on top of the stack.
//        visitVarInsn(Opcodes.ALOAD, 1)

        // Call CaptureOkHttpEventListenerFactory constructor
        visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                captureOkHttpEventListenerFactory,
                "<init>",
                "()V",
                false
        )

        // Call "eventListenerFactory" function of OkHttpClient.Builder passing CaptureOkHttpEventListenerFactory
        visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "okhttp3/OkHttpClient\$Builder",
                "eventListenerFactory",
                "(Lokhttp3/EventListener\$Factory;)Lokhttp3/OkHttpClient\$Builder;",
                false
        )
    }
}