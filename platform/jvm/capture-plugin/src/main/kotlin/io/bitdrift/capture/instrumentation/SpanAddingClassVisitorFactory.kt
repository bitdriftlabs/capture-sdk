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

package io.bitdrift.capture.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import io.bitdrift.capture.CapturePlugin
import io.bitdrift.capture.extension.InstrumentationExtension.OkHttpInstrumentationType
import io.bitdrift.capture.instrumentation.okhttp.OkHttpEventListener
import io.bitdrift.capture.instrumentation.util.findClassReader
import io.bitdrift.capture.instrumentation.util.findClassWriter
import io.bitdrift.capture.instrumentation.util.isMinifiedClass
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.objectweb.asm.ClassVisitor
import java.io.File

abstract class SpanAddingClassVisitorFactory : AsmClassVisitorFactory<SpanAddingClassVisitorFactory.SpanAddingParameters> {

    interface SpanAddingParameters : InstrumentationParameters {
        @get:Input
        val debug: Property<Boolean>

        @get:Input
        val okHttpInstrumentationType: Property<OkHttpInstrumentationType>

        @get:Internal
        val tmpDir: Property<File>

        @get:Internal
        var _instrumentable: ClassInstrumentable?
    }

    private val instrumentable: ClassInstrumentable
        get() {
            val memoized = parameters.get()._instrumentable
            if (memoized != null) {
                return memoized
            }

            val instrumentable = ChainedInstrumentable(
                    listOfNotNull(
                            OkHttpEventListener()
                    )
            )
            CapturePlugin.logger.info(
                    "Instrumentable: $instrumentable"
            )
            parameters.get()._instrumentable = instrumentable
            return instrumentable
        }


    override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        val className = classContext.currentClassData.className

        val classReader = nextClassVisitor.findClassWriter()?.findClassReader()
        val isMinifiedClass = classReader?.isMinifiedClass() ?: false
        if (isMinifiedClass) {
            CapturePlugin.logger.info(
                    "$className skipped from instrumentation because it's a minified class."
            )
            return nextClassVisitor
        }

        return instrumentable.getVisitor(
                classContext,
                instrumentationContext.apiVersion.get(),
                nextClassVisitor,
                parameters = parameters.get()
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean =
            instrumentable.isInstrumentable(classData.toClassContext())
}
