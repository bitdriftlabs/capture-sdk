// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.ui.designsystem.BdButtonSize
import io.bitdrift.gradletestapp.ui.designsystem.BdPrimaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Feature flag testing card
 */
@Composable
fun FeatureFlagsTestingCard(
    onAddVariantFlag: (Boolean) -> Unit,
    onAddManyFeatureFlags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var variantFlagValue by remember { mutableStateOf(true) }

    BdSectionCard(
        title = "Set Feature Flags",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Variant flag",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = BitdriftColors.TextPrimary,
            )

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = variantFlagValue,
                    onCheckedChange = { variantFlagValue = it },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = BitdriftColors.Primary,
                            checkmarkColor = BitdriftColors.Background,
                            uncheckedColor = BitdriftColors.BorderStrong,
                        ),
                )
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BitdriftColors.TextPrimary,
                )
            }

            BdPrimaryButton(
                text = "Record",
                onClick = { onAddVariantFlag(variantFlagValue) },
                size = BdButtonSize.Compact,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Multiple flags",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = BitdriftColors.TextPrimary,
            )

            BdPrimaryButton(
                text = "Record",
                onClick = onAddManyFeatureFlags,
                size = BdButtonSize.Compact,
            )
        }
    }
}
