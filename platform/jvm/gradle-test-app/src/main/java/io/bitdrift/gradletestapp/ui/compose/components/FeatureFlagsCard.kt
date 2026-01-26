// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Network Testing Card component
 */
@Composable
fun FeatureFlagsTestingCard(
    onAddVariantFlag: (Boolean) -> Unit,
    onAddManyFeatureFlags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var variantFlagValue by remember { mutableStateOf(true) }
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = BitdriftColors.BackgroundPaper,
            ),
        shape = MaterialTheme.shapes.medium,
        border =
            BorderStroke(
                width = 1.dp,
                color = BitdriftColors.Border.copy(alpha = 0.3f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Set Feature Flags",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "variant_flag",
                    modifier = Modifier
                        .weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BitdriftColors.TextPrimary,
                )

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Checkbox(
                        checked = variantFlagValue,
                        onCheckedChange = { variantFlagValue = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BitdriftColors.TextPrimary,
                            uncheckedColor = BitdriftColors.Border,
                        ),
                    )
                    Text(
                        text = "Enabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BitdriftColors.TextPrimary,
                    )
                }

                Button(
                    onClick = { onAddVariantFlag(variantFlagValue) },
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitdriftColors.TextPrimary,
                            contentColor = BitdriftColors.BackgroundPaper,
                        ),
                ) {
                    Text("Record")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Multiple flags",
                    modifier = Modifier
                        .weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BitdriftColors.TextPrimary,
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onAddManyFeatureFlags,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitdriftColors.TextPrimary,
                            contentColor = BitdriftColors.BackgroundPaper,
                        ),
                ) {
                    Text("Record")
                }
            }
        }
    }
}
