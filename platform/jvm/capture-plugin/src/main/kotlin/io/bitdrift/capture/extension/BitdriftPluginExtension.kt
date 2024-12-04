// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.extension

import org.gradle.api.Project
import javax.inject.Inject

abstract class BitdriftPluginExtension @Inject constructor(project: Project) {
    private val objects = project.objects

    val instrumentation: InstrumentationExtension = objects.newInstance(InstrumentationExtension::class.java)
}