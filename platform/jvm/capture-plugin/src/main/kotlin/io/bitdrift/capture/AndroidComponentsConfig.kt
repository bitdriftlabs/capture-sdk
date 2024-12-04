// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.bitdrift.capture.CapturePlugin.Companion.sep
import org.gradle.api.Project
import java.io.File

fun AndroidComponentsExtension<*, *, *>.configure(
        project: Project,
        extension: BitdriftPluginExtension,
) {
    // temp folder for sentry-related stuff
    val tmpDir = File("${project.buildDir}${sep}tmp${sep}sentry")
    tmpDir.mkdirs()

    onVariants { variant ->
        if (extension.instrumentation.enabled.get()) {
            variant.configureInstrumentation(
                    SpanAddingClassVisitorFactory::class.java,
                    InstrumentationScope.ALL,
                    FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
            ) { params ->
                params.tmpDir.set(tmpDir)
                params.debug.set(false)
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
            instrumentationParamsConfig
    )
    instrumentation.setAsmFramesComputationMode(mode)
}