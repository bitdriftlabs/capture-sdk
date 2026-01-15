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

package io.bitdrift.capture

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.bitdrift.capture.CapturePlugin.Companion.sep
import io.bitdrift.capture.extension.BitdriftPluginExtension
import io.bitdrift.capture.instrumentation.SpanAddingClassVisitorFactory
import org.gradle.api.Project
import java.io.File

fun AndroidComponentsExtension<*, *, *>.configure(
    project: Project,
    extension: BitdriftPluginExtension,
) {
    // Temp folder for outputting debug logs
    val tmpDir = File("${project.layout.buildDirectory}${sep}tmp${sep}bitdrift")
    tmpDir.mkdirs()

    onVariants { variant ->
        val enableOkHttp = extension.instrumentation.automaticOkHttpInstrumentation.get()
        val enableWebView = extension.instrumentation.automaticWebViewInstrumentation.get()

        if (enableOkHttp || enableWebView) {
            variant.configureInstrumentation(
                SpanAddingClassVisitorFactory::class.java,
                InstrumentationScope.ALL,
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
            ) { params ->
                params.tmpDir.set(tmpDir)
                params.debug.set(extension.instrumentation.debug)
                params.okHttpInstrumentationType.set(extension.instrumentation.okHttpInstrumentationType)
                params.enableOkHttpInstrumentation.set(enableOkHttp)
                params.enableWebViewInstrumentation.set(enableWebView)
            }
        }
    }
}

private fun <T : InstrumentationParameters> Variant.configureInstrumentation(
    classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<T>>,
    scope: InstrumentationScope,
    mode: FramesComputationMode,
    instrumentationParamsConfig: (T) -> Unit,
) {
    instrumentation.transformClassesWith(
        classVisitorFactoryImplClass,
        scope,
        instrumentationParamsConfig,
    )
    instrumentation.setAsmFramesComputationMode(mode)
}
