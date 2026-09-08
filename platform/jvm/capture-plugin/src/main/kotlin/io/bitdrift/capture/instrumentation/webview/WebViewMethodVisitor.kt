// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.webview

import com.android.build.api.instrumentation.ClassContext
import io.bitdrift.capture.extension.InstrumentationExtension.WebViewAutomaticInstrumentationMode
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * ASM MethodVisitor that intercepts calls to WebView.loadUrl() and injects
 * mode-aware WebView instrumentation before each call.
 *
 * This works by scanning for INVOKEVIRTUAL instructions that target:
 * - android/webkit/WebView.loadUrl(String)V
 * - android/webkit/WebView.loadUrl(String, Map)V
 *
 * When found, it duplicates the WebView reference on the stack and calls the appropriate
 * [WebViewCapture] automatic instrumentation entry point before the original loadUrl() call proceeds.
 *
 * Transforms:
 *   webView.loadUrl(url)
 *
 * Into:
 *   WebViewCaptureInternals.instrumentInternally(webView, logger, runtimeProvider, mode)
 *   webView.loadUrl(url)
 */
class WebViewMethodVisitor(
    apiVersion: Int,
    methodVisitor: MethodVisitor,
    private val classContext: ClassContext,
    private val mode: WebViewAutomaticInstrumentationMode = WebViewAutomaticInstrumentationMode.FULL,
) : MethodVisitor(apiVersion, methodVisitor) {

    companion object {
        private const val WEBVIEW_CLASS_INTERNAL_NAME = "android/webkit/WebView"
        private const val WEBVIEW_CLASS = "android.webkit.WebView"
        private const val LOAD_URL_METHOD = "loadUrl"
        private const val LOAD_URL_DESCRIPTOR = "(Ljava/lang/String;)V"
        private const val LOAD_URL_WITH_HEADERS_DESCRIPTOR = "(Ljava/lang/String;Ljava/util/Map;)V"

        private const val WEBVIEW_CAPTURE_INTERNALS_CLASS = "io/bitdrift/capture/webview/WebViewCaptureInternals"
        private const val INSTRUMENT_METHOD = "instrumentInternally"
        private const val INSTRUMENT_DESCRIPTOR = "(Landroid/webkit/WebView;Lio/bitdrift/capture/ILogger;Lio/bitdrift/capture/IRuntimeProvider;Lio/bitdrift/capture/webview/WebViewInstrumentationMode;)V"
        private const val WEBVIEW_INSTRUMENTATION_MODE_CLASS = "io/bitdrift/capture/webview/WebViewInstrumentationMode"
        private const val CAPTURE_RUNTIME_PROVIDER_CLASS = "io/bitdrift/capture/CaptureRuntimeProvider"
        private const val CAPTURE_RUNTIME_PROVIDER_INSTANCE_DESCRIPTOR = "Lio/bitdrift/capture/CaptureRuntimeProvider;"
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean,
    ) {
        val isWebViewLoadUrl = opcode == Opcodes.INVOKEVIRTUAL &&
            owner.isWebViewClassOwner(classContext) &&
            name == LOAD_URL_METHOD &&
            (descriptor == LOAD_URL_DESCRIPTOR || descriptor == LOAD_URL_WITH_HEADERS_DESCRIPTOR)

        if (isWebViewLoadUrl) {
            // Before the loadUrl call, we need to:
            // 1. Duplicate the WebView reference that's already on the stack
            // 2. Call the mode-aware internal WebView instrumentation entry point
            // 3. Let the original loadUrl() proceed

            // Stack before: [..., webView, url] or [..., webView, url, headers]
            // We need to get a copy of webView to pass to instrument()
            when (descriptor) {
                LOAD_URL_DESCRIPTOR -> {
                    // Stack: webView, url
                    // DUP_X1: url, webView, url
                    // POP: url, webView
                    // DUP_X1: webView, url, webView
                    // INVOKESTATIC instrument: webView, url
                    // Then original INVOKEVIRTUAL loadUrl proceeds
                    mv.visitInsn(Opcodes.DUP_X1)  // url, webView, url
                    mv.visitInsn(Opcodes.POP)      // url, webView
                    mv.visitInsn(Opcodes.DUP_X1)  // webView, url, webView
                    instrumentWebView()
                    // Stack now: webView, url - ready for original loadUrl
                }
                LOAD_URL_WITH_HEADERS_DESCRIPTOR -> {
                    // Stack: webView, url, headers
                    // We need to extract webView, call instrument, then restore stack
                    // DUP2_X1: url, headers, webView, url, headers
                    // POP2: url, headers, webView
                    // DUP_X2: webView, url, headers, webView
                    // INVOKESTATIC instrument: webView, url, headers
                    mv.visitInsn(Opcodes.DUP2_X1)  // url, headers, webView, url, headers
                    mv.visitInsn(Opcodes.POP2)      // url, headers, webView
                    mv.visitInsn(Opcodes.DUP_X2)   // webView, url, headers, webView
                    instrumentWebView()
                    // Stack now: webView, url, headers - ready for original loadUrl
                }
            }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    private fun instrumentWebView() {
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitFieldInsn(
            Opcodes.GETSTATIC,
            CAPTURE_RUNTIME_PROVIDER_CLASS,
            "INSTANCE",
            CAPTURE_RUNTIME_PROVIDER_INSTANCE_DESCRIPTOR,
        )
        mv.visitFieldInsn(
            Opcodes.GETSTATIC,
            WEBVIEW_INSTRUMENTATION_MODE_CLASS,
            mode.internalModeName,
            "L$WEBVIEW_INSTRUMENTATION_MODE_CLASS;",
        )
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            WEBVIEW_CAPTURE_INTERNALS_CLASS,
            INSTRUMENT_METHOD,
            INSTRUMENT_DESCRIPTOR,
            false,
        )
    }

    private val WebViewAutomaticInstrumentationMode.internalModeName: String
        get() =
            when (this) {
                WebViewAutomaticInstrumentationMode.FULL -> "AUTOMATIC_FULL"
                WebViewAutomaticInstrumentationMode.ONLY_IF_JAVASCRIPT_ALREADY_ENABLED ->
                    "AUTOMATIC_JAVASCRIPT_ENABLED_ONLY"
            }

    private fun String?.isWebViewClassOwner(classContext: ClassContext): Boolean {
        if (this == null) {
            return false
        }

        if (this == WEBVIEW_CLASS_INTERNAL_NAME) {
            return true
        }

        val ownerFullyQualifyClassName = replace('/', '.')
        val classData = runCatching {
            classContext.loadClassData(ownerFullyQualifyClassName)
        }.getOrNull() ?: return false

        return WEBVIEW_CLASS in classData.superClasses
    }
}
