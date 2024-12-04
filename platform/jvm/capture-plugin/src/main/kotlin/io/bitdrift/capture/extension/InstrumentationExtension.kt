// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.extension

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

open class InstrumentationExtension @Inject constructor(objects: ObjectFactory) {

    val enabled: Property<Boolean> = objects.property(Boolean::class.java)
            .convention(true)

    val debug: Property<Boolean> = objects.property(Boolean::class.java).convention(
            false
    )
}