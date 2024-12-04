// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import io.bitdrift.capture.instrumentation.ClassInstrumentable
import io.bitdrift.capture.instrumentation.okhttp.OkHttpEventListener
import io.bitdrift.capture.instrumentation.toClassContext
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
