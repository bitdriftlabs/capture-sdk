// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe

/**
 * Prevents relying mutableMap.putAll as could lead to runtime crashes if a map passed into
 * Map<String,String> is passed into
 * is created from Java call site with null values.
 *
 * Suggestion will be to use MapExtensionsKt.putAllSafe(it)
 *
 * For more info check BIT-5914
 */
class UnsafeMutableMapPutAllRule(config: Config) : Rule(config) {

    override val issue = Issue(
        id = "UnsafePutAllUsage",
        severity = Severity.Warning,
        description = "Avoid unsafe putAll usage on MutableMap; Java interop may allow nulls through.",
        debt = Debt.FIVE_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != "putAll") return
        val receiver =
            (expression.parent as? KtDotQualifiedExpression)?.receiverExpression ?: return
        val receiverType = bindingContext.getType(receiver) ?: return
        val fqName = receiverType.constructor.declarationDescriptor?.fqNameUnsafe?.asString()
        if (fqName == "kotlin.collections.MutableMap") {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    message = "Calling putAll() on a mutable map may risk null values from Java interop. Use putAllSafe() instead."
                )
            )
        }
    }
}