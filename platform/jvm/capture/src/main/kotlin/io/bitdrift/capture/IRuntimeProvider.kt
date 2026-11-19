// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.common.RuntimeStringConfig

internal interface IRuntimeProvider {
    fun isRuntimeFeatureEnabled(feature: RuntimeFeature): Boolean

    fun getRuntimeConfigValue(config: RuntimeConfig): Int

    fun getRuntimeStringConfigValue(config: RuntimeStringConfig): String
}

internal object CaptureRuntimeProvider : IRuntimeProvider {
    override fun isRuntimeFeatureEnabled(feature: RuntimeFeature): Boolean =
        getLoggerProvider()?.isRuntimeFeatureEnabled(feature) ?: feature.defaultValue

    override fun getRuntimeConfigValue(config: RuntimeConfig): Int =
        getLoggerProvider()?.getRuntimeConfigValue(config) ?: config.defaultValue

    override fun getRuntimeStringConfigValue(config: RuntimeStringConfig): String =
        getLoggerProvider()?.getRuntimeStringConfigValue(config) ?: config.defaultValue

    private fun getLoggerProvider(): IRuntimeProvider? = Capture.logger() as? IRuntimeProvider
}
