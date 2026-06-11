// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.webview

import io.bitdrift.capture.instrumentation.fakes.TestClassContext
import io.bitdrift.capture.instrumentation.fakes.TestClassData
import org.junit.Test
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals

class WebViewMethodVisitorTest {
    @Test
    fun `instruments base webview owner`() {
        val recorder = RecordingMethodVisitor()
        val sut = WebViewMethodVisitor(Opcodes.ASM7, recorder, TestClassContext("com.example.Caller"))

        sut.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "android/webkit/WebView",
            "loadUrl",
            "(Ljava/lang/String;)V",
            false,
        )

        assertEquals(
            listOf(
                "INSN:DUP_X1",
                "INSN:POP",
                "INSN:DUP_X1",
                "METHOD:INVOKESTATIC:io/bitdrift/capture/webview/WebViewCapture.instrument(Landroid/webkit/WebView;)V",
                "METHOD:INVOKEVIRTUAL:android/webkit/WebView.loadUrl(Ljava/lang/String;)V",
            ),
            recorder.events,
        )
    }

    @Test
    fun `instruments webview subclass loadUrl owner`() {
        val recorder = RecordingMethodVisitor()
        val sut =
            WebViewMethodVisitor(
                Opcodes.ASM7,
                recorder,
                TestClassContext("com/example/Caller") { className ->
                    when (className) {
                        "com.reactnativecommunity.webview.RNCWebView" ->
                            TestClassData(
                                className = className,
                                superClasses = listOf(
                                    "android.webkit.WebView",
                                    "android.view.View",
                                ),
                            )

                        else -> null
                    }
                },
            )

        sut.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "com/reactnativecommunity/webview/RNCWebView",
            "loadUrl",
            "(Ljava/lang/String;)V",
            false,
        )

        assertEquals(
            listOf(
                "INSN:DUP_X1",
                "INSN:POP",
                "INSN:DUP_X1",
                "METHOD:INVOKESTATIC:io/bitdrift/capture/webview/WebViewCapture.instrument(Landroid/webkit/WebView;)V",
                "METHOD:INVOKEVIRTUAL:com/reactnativecommunity/webview/RNCWebView.loadUrl(Ljava/lang/String;)V",
            ),
            recorder.events,
        )
    }

    @Test
    fun `skips unrelated loadUrl owner`() {
        val recorder = RecordingMethodVisitor()
        val sut = WebViewMethodVisitor(Opcodes.ASM7, recorder, TestClassContext("com/example/Caller"))

        sut.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "com/example/CustomView",
            "loadUrl",
            "(Ljava/lang/String;)V",
            false,
        )

        assertEquals(
            listOf(
                "METHOD:INVOKEVIRTUAL:com/example/CustomView.loadUrl(Ljava/lang/String;)V",
            ),
            recorder.events,
        )
    }

    @Test
    fun `skips owner when class lookup fails`() {
        val recorder = RecordingMethodVisitor()
        val sut = WebViewMethodVisitor(Opcodes.ASM7, recorder, TestClassContext("com.example.Caller"))

        sut.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "com/reactnativecommunity/webview/RNCWebView",
            "loadUrl",
            "(Ljava/lang/String;)V",
            false,
        )

        assertEquals(
            listOf(
                "METHOD:INVOKEVIRTUAL:com/reactnativecommunity/webview/RNCWebView.loadUrl(Ljava/lang/String;)V",
            ),
            recorder.events,
        )
    }
}

private class RecordingMethodVisitor : MethodVisitor(Opcodes.ASM7) {
    val events = mutableListOf<String>()

    override fun visitInsn(opcode: Int) {
        events += "INSN:${opcodeName(opcode)}"
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean,
    ) {
        events += "METHOD:${opcodeName(opcode)}:$owner.$name$descriptor"
    }

    private fun opcodeName(opcode: Int): String =
        when (opcode) {
            Opcodes.DUP_X1 -> "DUP_X1"
            Opcodes.POP -> "POP"
            Opcodes.INVOKESTATIC -> "INVOKESTATIC"
            Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
            else -> opcode.toString()
        }
}
