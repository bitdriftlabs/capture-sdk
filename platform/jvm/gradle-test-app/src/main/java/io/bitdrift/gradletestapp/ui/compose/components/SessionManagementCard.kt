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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Session Management Card component
 */
@Composable
fun SessionManagementCard(
    uiState: AppState,
    onStartNewSession: () -> Unit,
    onGenerateDeviceCode: () -> Unit,
    onCopySessionUrl: () -> Unit,
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
                text = "Session Management",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            SessionStrategyField(sessionStrategy = uiState.config.sessionStrategy)

            if (uiState.session.sessionId != null) {
                SessionIdField(
                    sessionId = uiState.session.sessionId,
                    onCopySessionUrl = onCopySessionUrl,
                )
            }

            SessionActionButtons(
                onCopySessionUrl = onCopySessionUrl,
                onStartNewSession = onStartNewSession,
            )

            GenerateDeviceCodeButton(
                onClick = onGenerateDeviceCode,
            )

            if (uiState.session.deviceCode != null) {
                DeviceCodeField(deviceCode = uiState.session.deviceCode)
            } else {
                Text(
                    text = "No Code Generated",
                    style = MaterialTheme.typography.bodySmall,
                    color = BitdriftColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun SessionStrategyField(sessionStrategy: String) {
    OutlinedTextField(
        value = sessionStrategy,
        onValueChange = {},
        label = { Text("Session Strategy", color = BitdriftColors.TextSecondary) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        singleLine = true,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = BitdriftColors.TextSecondary,
                unfocusedTextColor = BitdriftColors.TextSecondary,
                focusedBorderColor = BitdriftColors.Border,
                unfocusedBorderColor = BitdriftColors.Border,
                focusedLabelColor = BitdriftColors.TextSecondary,
                unfocusedLabelColor = BitdriftColors.TextSecondary,
                cursorColor = BitdriftColors.TextBright,
            ),
    )
}

@Composable
private fun SessionIdField(
    sessionId: String,
    onCopySessionUrl: () -> Unit,
) {
    OutlinedTextField(
        value = sessionId,
        onValueChange = {},
        label = { Text("Session ID", color = BitdriftColors.TextSecondary) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
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
        trailingIcon = {
            TextButton(onClick = onCopySessionUrl) {
                Text("Copy", color = BitdriftColors.TextBright)
            }
        },
    )
}

@Composable
private fun SessionActionButtons(
    onCopySessionUrl: () -> Unit,
    onStartNewSession: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCopySessionUrl,
            modifier = Modifier.weight(1f),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = BitdriftColors.TextPrimary,
                ),
        ) {
            Text("Copy URL")
        }

        OutlinedButton(
            onClick = onStartNewSession,
            modifier = Modifier.weight(1f),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = BitdriftColors.TextPrimary,
                ),
        ) {
            Text("New Session")
        }
    }
}

@Composable
private fun GenerateDeviceCodeButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = BitdriftColors.TextPrimary,
            ),
    ) {
        Text("Generate Device Code")
    }
}

@Composable
private fun DeviceCodeField(deviceCode: String) {
    OutlinedTextField(
        value = deviceCode,
        onValueChange = {},
        label = { Text("Device Code", color = BitdriftColors.TextSecondary) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
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
}
