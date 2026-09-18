// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.ui.designsystem.BdButtonSize
import io.bitdrift.gradletestapp.ui.designsystem.BdPrimaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Exercises the Live Session Commands prototype (`Logger.registerCommand`). "Invoke" stands in
 * for the debugger/workflow actually triggering the command -- that native trigger path doesn't
 * exist yet, see `Logger.invokeCommandForTesting`.
 */
@Composable
fun CommandsTestingCard(
    isRegistered: Boolean,
    lastResult: String?,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onInvoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = "Live Session Commands (prototype)",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isRegistered) "\"flip_flag\" registered" else "\"flip_flag\" not registered",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = BitdriftColors.TextPrimary,
            )

            BdPrimaryButton(
                text = if (isRegistered) "Unregister" else "Register",
                onClick = { if (isRegistered) onUnregister() else onRegister() },
                size = BdButtonSize.Compact,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Simulate the debugger invoking it",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = BitdriftColors.TextPrimary,
            )

            BdPrimaryButton(
                text = "Invoke",
                onClick = onInvoke,
                size = BdButtonSize.Compact,
                enabled = isRegistered,
            )
        }

        lastResult?.let { result ->
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = BitdriftColors.TextSecondary,
            )
        }
    }
}
