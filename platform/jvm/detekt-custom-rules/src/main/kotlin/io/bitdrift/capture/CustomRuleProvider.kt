// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Entry point for custom bitdrift detekt rules
 */
class CustomRuleProvider : RuleSetProvider {
    override val ruleSetId: String = "bitdrift-custom-rules"

    override fun instance(config: Config): RuleSet {
        return RuleSet(
            id = ruleSetId,
            rules = listOf(
                UnsafeMutableMapPutAllRule(config)
                // Append more custom rules as needed here
            )
        )
    }
}
