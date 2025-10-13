// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.bitdrift.capture.LogLevel
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppExitReason
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Testing Tools Card component
 */
@Composable
fun TestingToolsCard(
    uiState: AppState,
    onLogLevelChange: (LogLevel) -> Unit,
    onAppExitReasonChange: (AppExitReason) -> Unit,
    onLogMessage: () -> Unit,
    onForceAppExit: () -> Unit,
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
                text = stringResource(id = R.string.testing_tools),
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            LogLevelSelector(
                selectedLogLevel = uiState.config.selectedLogLevel,
                onLogLevelChange = onLogLevelChange,
            )

            Button(
                onClick = onLogMessage,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitdriftColors.Primary,
                        contentColor = Color.White,
                    ),
            ) {
                Text("Log Message")
            }

            AppExitReasonSelector(
                selectedAppExitReason = uiState.diagnostics.selectedAppExitReason,
                onAppExitReasonChange = onAppExitReasonChange,
            )

            Button(
                onClick = onForceAppExit,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitdriftColors.Error,
                        contentColor = BitdriftColors.TextBright,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Force App Exit")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogLevelSelector(
    selectedLogLevel: LogLevel,
    onLogLevelChange: (LogLevel) -> Unit,
) {
    var expandedLogLevel by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expandedLogLevel,
        onExpandedChange = { expandedLogLevel = !expandedLogLevel },
    ) {
        OutlinedTextField(
            value = selectedLogLevel.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Log Level", color = BitdriftColors.TextSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLogLevel) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BitdriftColors.TextBright,
                    unfocusedTextColor = BitdriftColors.TextBright,
                    focusedBorderColor = BitdriftColors.Primary,
                    unfocusedBorderColor = BitdriftColors.Border,
                    focusedLabelColor = BitdriftColors.Primary,
                    unfocusedLabelColor = BitdriftColors.TextSecondary,
                    cursorColor = BitdriftColors.TextBright,
                    selectionColors =
                        TextSelectionColors(
                            handleColor = BitdriftColors.TextBright,
                            backgroundColor = BitdriftColors.Primary.copy(alpha = 0.3f),
                        ),
                ),
        )
        ExposedDropdownMenu(
            expanded = expandedLogLevel,
            onDismissRequest = { expandedLogLevel = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LogLevel.values().forEach { level ->
                DropdownMenuItem(
                    text = {
                        Text(
                            level.name,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onLogLevelChange(level)
                        expandedLogLevel = false
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppExitReasonSelector(
    selectedAppExitReason: AppExitReason,
    onAppExitReasonChange: (AppExitReason) -> Unit,
) {
    var expandedExitReason by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expandedExitReason,
        onExpandedChange = { expandedExitReason = !expandedExitReason },
    ) {
        OutlinedTextField(
            value = selectedAppExitReason.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Exit Reason", color = BitdriftColors.TextSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedExitReason) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BitdriftColors.TextBright,
                    unfocusedTextColor = BitdriftColors.TextBright,
                    focusedBorderColor = BitdriftColors.Primary,
                    unfocusedBorderColor = BitdriftColors.Border,
                    focusedLabelColor = BitdriftColors.Primary,
                    unfocusedLabelColor = BitdriftColors.TextSecondary,
                    cursorColor = BitdriftColors.TextBright,
                    selectionColors =
                        TextSelectionColors(
                            handleColor = BitdriftColors.TextBright,
                            backgroundColor = BitdriftColors.Primary.copy(alpha = 0.3f),
                        ),
                ),
        )
        ExposedDropdownMenu(
            expanded = expandedExitReason,
            onDismissRequest = { expandedExitReason = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AppExitReason.values().forEach { reason ->
                DropdownMenuItem(
                    text = {
                        Text(
                            reason.name,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onAppExitReasonChange(reason)
                        expandedExitReason = false
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )
            }
        }
    }
}
