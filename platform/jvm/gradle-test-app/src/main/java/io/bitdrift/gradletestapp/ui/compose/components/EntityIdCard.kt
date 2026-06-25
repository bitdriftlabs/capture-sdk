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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@Composable
fun EntityIdCard(
    entityIdValue: String,
    onClearEntityId: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = "Entity ID",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            OutlinedTextField(
                value = entityIdValue,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Entity ID", color = BitdriftColors.TextSecondary) },
                readOnly = true,
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BitdriftColors.TextBright,
                        unfocusedTextColor = BitdriftColors.TextBright,
                        focusedBorderColor = BitdriftColors.Primary,
                        unfocusedBorderColor = BitdriftColors.Border,
                        focusedLabelColor = BitdriftColors.Primary,
                        unfocusedLabelColor = BitdriftColors.TextSecondary,
                        cursorColor = BitdriftColors.TextBright,
                    ),
            )

            OutlinedButton(
                onClick = onClearEntityId,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = BitdriftColors.TextPrimary,
                    ),
            ) {
                Text("Clear Entity ID")
            }
        }
    }
}
