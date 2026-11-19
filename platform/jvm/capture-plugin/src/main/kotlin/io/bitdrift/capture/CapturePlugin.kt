// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.android.build.api.variant.AndroidComponentsExtension
import io.bitdrift.capture.extension.BitdriftPluginExtension
import io.bitdrift.capture.task.CLIUploadMappingTask
import io.bitdrift.capture.task.CLIUploadSourceMapTask
import io.bitdrift.capture.task.CLIUploadSymbolsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.LoggerFactory
import java.io.File
import javax.inject.Inject

abstract class CapturePlugin
    @Inject
    constructor() : Plugin<Project> {
        override fun apply(target: Project) {
            val extension = target.extensions.create("bitdrift", BitdriftPluginExtension::class.java, target)

            target.pluginManager.withPlugin("com.android.application") {
                val androidComponentsExt =
                    target.extensions.getByType(AndroidComponentsExtension::class.java)

                androidComponentsExt.configure(
                    target,
                    extension,
                )
            }

            target.tasks.register("bdUploadMapping", CLIUploadMappingTask::class.java) { task ->
                task.description = "Upload mapping to Bitdrift"
                task.group = "Upload"
                task.buildDir.set(target.layout.buildDirectory)
                task.baseDomain.set(extension.baseDomain)
            }

            target.tasks.register("bdUploadSymbols", CLIUploadSymbolsTask::class.java) { task ->
                task.description = "Upload symbols to Bitdrift"
                task.group = "Upload"
                task.buildDir.set(target.layout.buildDirectory)
                task.baseDomain.set(extension.baseDomain)
            }

            target.tasks.register("bdUploadSourceMap", CLIUploadSourceMapTask::class.java) { task ->
                task.description = "Upload source map to Bitdrift"
                task.group = "Upload"
                task.buildDir.set(target.layout.buildDirectory)
                task.baseDomain.set(extension.baseDomain)
            }

            target.tasks.register("bdUpload") { task ->
                task.description = "Upload all symbol and mapping files to Bitdrift"
                task.group = "Upload"
                task.dependsOn("bdUploadMapping", "bdUploadSymbols", "bdUploadSourceMap")
            }
        }

        companion object {
            internal val sep = File.separator

            internal val logger by lazy {
                LoggerFactory.getLogger(CapturePlugin::class.java)
            }
        }
    }
