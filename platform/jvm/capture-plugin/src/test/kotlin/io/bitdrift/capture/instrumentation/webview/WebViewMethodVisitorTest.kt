// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.webview

import io.bitdrift.capture.instrumentation.fakes.TestClassContext
import io.bitdrift.capture.instrumentation.fakes.TestClassData
import io.bitdrift.capture.extension.InstrumentationExtension.WebViewAutomaticInstrumentationMode
import org.junit.Test
import org.objectweb.asm.Label
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
                "INSN:ACONST_NULL",
                "FIELD:GETSTATIC:io/bitdrift/capture/CaptureRuntimeProvider.INSTANCE:Lio/bitdrift/capture/CaptureRuntimeProvider;",
                "FIELD:GETSTATIC:io/bitdrift/capture/webview/WebViewInstrumentationMode.AUTOMATIC_FULL:Lio/bitdrift/capture/webview/WebViewInstrumentationMode;",
                "METHOD:INVOKESTATIC:io/bitdrift/capture/webview/WebViewCaptureInternals.instrumentInternally(Landroid/webkit/WebView;Lio/bitdrift/capture/ILogger;Lio/bitdrift/capture/IRuntimeProvider;Lio/bitdrift/capture/webview/WebViewInstrumentationMode;)V",
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
                "INSN:ACONST_NULL",
                "FIELD:GETSTATIC:io/bitdrift/capture/CaptureRuntimeProvider.INSTANCE:Lio/bitdrift/capture/CaptureRuntimeProvider;",
                "FIELD:GETSTATIC:io/bitdrift/capture/webview/WebViewInstrumentationMode.AUTOMATIC_FULL:Lio/bitdrift/capture/webview/WebViewInstrumentationMode;",
                "METHOD:INVOKESTATIC:io/bitdrift/capture/webview/WebViewCaptureInternals.instrumentInternally(Landroid/webkit/WebView;Lio/bitdrift/capture/ILogger;Lio/bitdrift/capture/IRuntimeProvider;Lio/bitdrift/capture/webview/WebViewInstrumentationMode;)V",
                "METHOD:INVOKEVIRTUAL:com/reactnativecommunity/webview/RNCWebView.loadUrl(Ljava/lang/String;)V",
            ),
            recorder.events,
        )
    }

    @Test
    fun `javascript enabled only mode passes automatic javascript enabled only mode`() {
        val recorder = RecordingMethodVisitor()
        val sut =
            WebViewMethodVisitor(
                Opcodes.ASM7,
                recorder,
                TestClassContext("com.example.Caller"),
                WebViewAutomaticInstrumentationMode.ONLY_IF_JAVASCRIPT_ALREADY_ENABLED,
            )

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
                "INSN:ACONST_NULL",
                "FIELD:GETSTATIC:io/bitdrift/capture/CaptureRuntimeProvider.INSTANCE:Lio/bitdrift/capture/CaptureRuntimeProvider;",
                "FIELD:GETSTATIC:io/bitdrift/capture/webview/WebViewInstrumentationMode.AUTOMATIC_JAVASCRIPT_ENABLED_ONLY:Lio/bitdrift/capture/webview/WebViewInstrumentationMode;",
                "METHOD:INVOKESTATIC:io/bitdrift/capture/webview/WebViewCaptureInternals.instrumentInternally(Landroid/webkit/WebView;Lio/bitdrift/capture/ILogger;Lio/bitdrift/capture/IRuntimeProvider;Lio/bitdrift/capture/webview/WebViewInstrumentationMode;)V",
                "METHOD:INVOKEVIRTUAL:android/webkit/WebView.loadUrl(Ljava/lang/String;)V",
            ),
            recorder.events,
        )
    }

    @Test
    fun `javascript enabled only mode preserves headers loadUrl arguments`() {
        val recorder = RecordingMethodVisitor()
        val sut =
            WebViewMethodVisitor(
                Opcodes.ASM7,
                recorder,
                TestClassContext("com.example.Caller"),
                WebViewAutomaticInstrumentationMode.ONLY_IF_JAVASCRIPT_ALREADY_ENABLED,
            )

        sut.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "android/webkit/WebView",
            "loadUrl",
            "(Ljava/lang/String;Ljava/util/Map;)V",
            false,
        )

        assertEquals(
            listOf(
                "INSN:DUP2_X1",
                "INSN:POP2",
                "INSN:DUP_X2",
                "INSN:ACONST_NULL",
                "FIELD:GETSTATIC:io/bitdrift/capture/CaptureRuntimeProvider.INSTANCE:Lio/bitdrift/capture/CaptureRuntimeProvider;",
                "FIELD:GETSTATIC:io/bitdrift/capture/webview/WebViewInstrumentationMode.AUTOMATIC_JAVASCRIPT_ENABLED_ONLY:Lio/bitdrift/capture/webview/WebViewInstrumentationMode;",
                "METHOD:INVOKESTATIC:io/bitdrift/capture/webview/WebViewCaptureInternals.instrumentInternally(Landroid/webkit/WebView;Lio/bitdrift/capture/ILogger;Lio/bitdrift/capture/IRuntimeProvider;Lio/bitdrift/capture/webview/WebViewInstrumentationMode;)V",
                "METHOD:INVOKEVIRTUAL:android/webkit/WebView.loadUrl(Ljava/lang/String;Ljava/util/Map;)V",
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

    override fun visitFieldInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
    ) {
        events += "FIELD:${opcodeName(opcode)}:$owner.$name:$descriptor"
    }

    private fun opcodeName(opcode: Int): String =
        when (opcode) {
            Opcodes.DUP_X1 -> "DUP_X1"
            Opcodes.DUP2_X1 -> "DUP2_X1"
            Opcodes.DUP_X2 -> "DUP_X2"
            Opcodes.POP -> "POP"
            Opcodes.POP2 -> "POP2"
            Opcodes.ACONST_NULL -> "ACONST_NULL"
            Opcodes.GETSTATIC -> "GETSTATIC"
            Opcodes.INVOKESTATIC -> "INVOKESTATIC"
            Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
            else -> opcode.toString()
        }
}
