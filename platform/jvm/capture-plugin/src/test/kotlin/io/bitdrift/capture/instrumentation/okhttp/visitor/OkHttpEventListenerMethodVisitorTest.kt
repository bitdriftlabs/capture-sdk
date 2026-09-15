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
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OkHttpEventListenerMethodVisitorTest {
    @Test
    fun `overwrite adds tracing interceptor after event listener factory`() {
        assertInstrumentation(
            OkHttpInstrumentationType.OVERWRITE,
            listOf(
                "eventListenerFactory",
                "interceptors",
                "interceptors",
                "interceptors",
                "addInterceptor",
            ),
            listOf(
                "io/bitdrift/capture/network/okhttp/CaptureOkHttpTracingInterceptor",
            ),
            listOf(Opcodes.IFEQ, Opcodes.IFNE),
        )
    }

    @Test
    fun `proxy adds tracing interceptor after event listener factory`() {
        assertInstrumentation(
            OkHttpInstrumentationType.PROXY,
            listOf(
                "getEventListenerFactory\$okhttp",
                "getEventListenerFactory\$okhttp",
                "eventListenerFactory",
                "interceptors",
                "interceptors",
                "interceptors",
                "addInterceptor",
            ),
            listOf(
                "io/bitdrift/capture/network/okhttp/CaptureOkHttpEventListenerFactory",
                "io/bitdrift/capture/network/okhttp/CaptureOkHttpTracingInterceptor",
            ),
            listOf(Opcodes.IFNE, Opcodes.IFEQ, Opcodes.IFNE),
        )
    }

    private fun assertInstrumentation(
        instrumentationType: OkHttpInstrumentationType,
        expectedCallNames: List<String>,
        expectedInstanceOfTypes: List<String>,
        expectedJumpOpcodes: List<Int>,
    ) {
        val recorder = instrument(instrumentationType)
        assertEquals(
            expectedCallNames,
            recorder.invocations
                .filter { it.owner == "okhttp3/OkHttpClient\$Builder" }
                .map(MethodInvocation::name),
        )
        assertEquals(
            expectedInstanceOfTypes,
            recorder.typeInstructions.filter { it.opcode == Opcodes.INSTANCEOF }.map(TypeInstruction::type),
        )
        assertEquals(
            expectedJumpOpcodes,
            recorder.jumpInstructions
                .filter { it.opcode != Opcodes.GOTO }
                .map(JumpInstruction::opcode),
        )
        assertTrue(recorder.labels.containsAll(recorder.jumpInstructions.map(JumpInstruction::label)))
        assertEquals(
            listOf("iterator", "remove", "add"),
            recorder.invocations
                .filter { it.owner == "java/util/List" }
                .map(MethodInvocation::name),
        )
    }

    private fun instrument(instrumentationType: OkHttpInstrumentationType): FakeMethodVisitor {
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

        return recorder
    }
}

private class FakeMethodVisitor : MethodVisitor(Opcodes.ASM7) {
    val invocations = mutableListOf<MethodInvocation>()
    val typeInstructions = mutableListOf<TypeInstruction>()
    val jumpInstructions = mutableListOf<JumpInstruction>()
    val labels = mutableListOf<Label>()

    override fun visitTypeInsn(
        opcode: Int,
        type: String,
    ) {
        typeInstructions += TypeInstruction(opcode, type)
        super.visitTypeInsn(opcode, type)
    }

    override fun visitJumpInsn(
        opcode: Int,
        label: Label,
    ) {
        jumpInstructions += JumpInstruction(opcode, label)
        super.visitJumpInsn(opcode, label)
    }

    override fun visitLabel(label: Label) {
        labels += label
        super.visitLabel(label)
    }

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

private data class TypeInstruction(
    val opcode: Int,
    val type: String,
)

private data class JumpInstruction(
    val opcode: Int,
    val label: Label,
)
