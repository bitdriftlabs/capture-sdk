// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.okhttp.visitor

import io.bitdrift.capture.extension.InstrumentationExtension.OkHttpInstrumentationType
import io.bitdrift.capture.instrumentation.MethodContext
import org.junit.Test
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals

class OkHttpEventListenerMethodVisitorTest {
    @Test
    fun `overwrite adds tracing interceptor after event listener factory`() {
        assertBuilderCallNames(
            OkHttpInstrumentationType.OVERWRITE,
            listOf("eventListenerFactory", "addInterceptor")
        )
    }

    @Test
    fun `proxy adds tracing interceptor after event listener factory`() {
        assertBuilderCallNames(
            OkHttpInstrumentationType.PROXY,
            listOf("getEventListenerFactory\$okhttp", "eventListenerFactory", "addInterceptor")
        )
    }

    private fun assertBuilderCallNames(
        instrumentationType: OkHttpInstrumentationType,
        expectedCallNames: List<String>,
    ) {
        val outputCallNames = instrument(instrumentationType).map(MethodInvocation::name)
        assertEquals(expectedCallNames, outputCallNames)
    }

    private fun instrument(instrumentationType: OkHttpInstrumentationType): List<MethodInvocation> {
        val recorder = FakeMethodVisitor()
        val visitor =
            OkHttpEventListenerMethodVisitor(
                Opcodes.ASM7,
                recorder,
                MethodContext(
                    Opcodes.ACC_PUBLIC,
                    "<init>",
                    "(Lokhttp3/OkHttpClient\$Builder;)V",
                    null,
                    null,
                ),
                instrumentationType,
            )

        visitor.visitCode()
        visitor.visitVarInsn(Opcodes.ALOAD, 0)
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        visitor.visitInsn(Opcodes.RETURN)
        visitor.visitMaxs(0, 0)
        visitor.visitEnd()

        return recorder.invocations.filter { it.owner == "okhttp3/OkHttpClient\$Builder" }
    }
}

private class FakeMethodVisitor : MethodVisitor(Opcodes.ASM7) {
    val invocations = mutableListOf<MethodInvocation>()

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
    ) {
        invocations += MethodInvocation(owner, name)
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
}

private data class MethodInvocation(
    val owner: String,
    val name: String,
)
