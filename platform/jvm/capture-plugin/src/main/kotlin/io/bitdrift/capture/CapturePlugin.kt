// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.android.build.api.variant.AndroidComponentsExtension
import io.bitdrift.capture.extension.BitdriftPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.LoggerFactory
import java.io.File
import javax.inject.Inject

abstract class CapturePlugin @Inject constructor() : Plugin<Project> {
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
    }

    companion object {
        internal val sep = File.separator

        internal val logger by lazy {
            LoggerFactory.getLogger(CapturePlugin::class.java)
        }
    }
}