// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.common.RuntimeStringConfig

/**
 * [Runtime] fake that answers with each flag's declared default unless a test overrides it, which
 * mirrors production more closely than a mock: a test only has to state what it changes.
 */
internal class FakeRuntime : Runtime {
    private val overrides = mutableMapOf<RuntimeFeature, Boolean>()

    fun setEnabled(
        feature: RuntimeFeature,
        enabled: Boolean,
    ) {
        overrides[feature] = enabled
    }

    override fun isEnabled(feature: RuntimeFeature): Boolean = overrides[feature] ?: feature.defaultValue

    override fun getConfigValue(config: RuntimeConfig): Int = config.defaultValue

    override fun getConfigValue(config: RuntimeStringConfig): String = config.defaultValue
}
