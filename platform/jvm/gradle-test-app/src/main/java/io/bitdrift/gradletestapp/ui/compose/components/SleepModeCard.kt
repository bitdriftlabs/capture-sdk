// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Sleep Mode controls card: checkbox + apply button
 */
@Composable
fun SleepModeCard(
    uiState: AppState,
    onToggle: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isChecked by remember(uiState.config.isSleepModeEnabled) { mutableStateOf(uiState.config.isSleepModeEnabled) }

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
                text = "Sleep Mode",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Switch(
                    checked = isChecked,
                    onCheckedChange = {
                        isChecked = it
                        if (uiState.session.isSdkInitialized) {
                            onToggle(it)
                        }
                    },
                    enabled = uiState.session.isSdkInitialized,
                )

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (isChecked) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BitdriftColors.Primary,
                        )
                    },
                    enabled = false,
                )
            }

            if (!uiState.session.isSdkInitialized) {
                Text(
                    text = "Initialize SDK to toggle sleep mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = BitdriftColors.TextTertiary,
                )
            }
        }
    }
}

@Preview
@Composable
fun SleepModeCardPreview() {
    SleepModeCard(
        AppState(),
        {},
    )
}
