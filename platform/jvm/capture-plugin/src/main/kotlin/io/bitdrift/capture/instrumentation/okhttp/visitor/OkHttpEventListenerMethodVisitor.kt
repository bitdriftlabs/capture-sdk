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

package io.bitdrift.capture.instrumentation.okhttp.visitor

import io.bitdrift.capture.instrumentation.MethodContext
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter

class OkHttpEventListenerMethodVisitor(
    apiVersion: Int,
    originalVisitor: MethodVisitor,
    instrumentableContext: MethodContext,
    val proxyEventListener: Boolean
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

        if (proxyEventListener) {
            addProxyingEventListener()
        } else {
            addOverwritingEventListener()
        }
    }

    private fun addOverwritingEventListener() {
        // Add the following call at the beginning of the constructor with the Builder parameter:
        // builder.eventListenerFactory(new CaptureOkHttpEventListenerFactory());

        // OkHttpClient.Builder is the parameter, retrieved here
        visitVarInsn(Opcodes.ALOAD, 1)

        // Let's declare the CaptureOkHttpEventListenerFactory variable
        visitTypeInsn(Opcodes.NEW, captureOkHttpEventListenerFactory)

        // The CaptureOkHttpEventListenerFactory constructor, which is called later, will consume the
        //  element without pushing anything back to the stack (<init> returns void).
        // Dup will give a reference to the CaptureOkHttpEventListenerFactory after the constructor call
        visitInsn(Opcodes.DUP)

        // Call CaptureOkHttpEventListenerFactory constructor passing "eventListenerFactory" as parameter
        visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            captureOkHttpEventListenerFactory,
            "<init>",
            "()V",
            false
        )

        // Call "eventListener" function of OkHttpClient.Builder passing CaptureOkHttpEventListenerFactory
        visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "okhttp3/OkHttpClient\$Builder",
            "eventListenerFactory",
            "(Lokhttp3/EventListener\$Factory;)Lokhttp3/OkHttpClient\$Builder;",
            false
        )
    }

    private fun addProxyingEventListener() {
        // Add the following call at the beginning of the constructor with the Builder parameter:
        // builder.eventListenerFactory(new CaptureOkHttpEventListenerFactory(builder.eventListenerFactory));

        // OkHttpClient.Builder is the parameter, retrieved here
        visitVarInsn(Opcodes.ALOAD, 1)

        // Let's declare the CaptureOkHttpEventListenerFactory variable
        visitTypeInsn(Opcodes.NEW, captureOkHttpEventListenerFactory)

        // The CaptureOkHttpEventListenerFactory constructor, which is called later, will consume the
        //  element without pushing anything back to the stack (<init> returns void).
        // Dup will give a reference to the CaptureOkHttpEventListenerFactory after the constructor call
        visitInsn(Opcodes.DUP)

        // Puts parameter OkHttpClient.Builder on top of the stack.
        visitVarInsn(Opcodes.ALOAD, 1)

        // Read the "eventListenerFactory" field from OkHttpClient.Builder
        visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "okhttp3/OkHttpClient\$Builder",
            "getEventListenerFactory\$okhttp",
            "()Lokhttp3/EventListener\$Factory;",
            false
        )

        // Call CaptureOkHttpEventListenerFactory constructor passing "eventListenerFactory" as parameter
        visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            captureOkHttpEventListenerFactory,
            "<init>",
            "(Lokhttp3/EventListener\$Factory;)V",
            false
        )

        // Call "eventListener" function of OkHttpClient.Builder passing CaptureOkHttpEventListenerFactory
        visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "okhttp3/OkHttpClient\$Builder",
            "eventListenerFactory",
            "(Lokhttp3/EventListener\$Factory;)Lokhttp3/OkHttpClient\$Builder;",
            false
        )
    }
}