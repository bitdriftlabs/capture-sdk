// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation.webview

import com.android.build.api.instrumentation.ClassContext
import io.bitdrift.capture.instrumentation.ClassInstrumentable
import io.bitdrift.capture.instrumentation.SpanAddingClassVisitorFactory
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Instrumentable that scans all classes for calls to WebView.loadUrl() and injects
 * WebViewCapture.instrument(webView) before each call.
 *
 * We use bytecode manipulation to automatically capture each WebView instance
 * at the point where loadUrl() is called, avoiding the need for manual instrumentation.
 */
class WebViewLoadUrlInstrumentable : ClassInstrumentable {
    override fun isInstrumentable(data: ClassContext): Boolean = true

    override fun getVisitor(
        instrumentableContext: ClassContext,
        apiVersion: Int,
        originalVisitor: ClassVisitor,
        parameters: SpanAddingClassVisitorFactory.SpanAddingParameters,
    ): ClassVisitor =
        WebViewClassVisitor(
            apiVersion = apiVersion,
            classVisitor = originalVisitor,
            className = instrumentableContext.currentClassData.className,
        )
}

/**
 * ClassVisitor that wraps all methods to scan for WebView.loadUrl() calls.
 */
class WebViewClassVisitor(
    apiVersion: Int,
    classVisitor: ClassVisitor,
    private val className: String,
) : ClassVisitor(apiVersion, classVisitor) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        return WebViewMethodVisitor(api, methodVisitor)
    }
}
