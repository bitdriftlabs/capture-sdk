// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature

internal class JniRuntime(
    private val logger: LoggerId,
) : Runtime {
    override fun isEnabled(feature: RuntimeFeature): Boolean = Jni.isRuntimeEnabled(logger, feature.featureName, feature.defaultValue)
}

internal object Jni {
    external fun isRuntimeEnabled(
        logger: LoggerId,
        feature: String,
        defaultValue: Boolean,
    ): Boolean
}
