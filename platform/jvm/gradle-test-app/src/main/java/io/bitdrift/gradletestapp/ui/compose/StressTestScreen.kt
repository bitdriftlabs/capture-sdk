// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.JankType
import io.bitdrift.gradletestapp.data.model.StressTestAction
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StressTestScreen(
    onAction: (AppAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        containerColor = BitdriftColors.Background,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                MemoryPressureCard(onAction = onAction)
            }
            item {
                JankyFramesCard(onAction = onAction)
            }
            item {
                StrictModeCard(onAction = onAction)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryPressureCard(onAction: (AppAction) -> Unit) {
    var selectedPercent by remember { mutableIntStateOf(95) }
    val percentOptions = listOf(50, 70, 80, 90, 95, 98, 100)

    StressTestCard(title = stringResource(id = R.string.memory_pressure)) {
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = "$selectedPercent%",
                onValueChange = {},
                readOnly = true,
                label = { Text("Target %", color = BitdriftColors.TextSecondary) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                percentOptions.forEach { percent ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "$percent%",
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            selectedPercent = percent
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onAction(StressTestAction.IncreaseMemoryPressure(selectedPercent)) },
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BitdriftColors.Primary,
                    contentColor = Color.White,
                ),
        ) {
            Text("Increase to $selectedPercent% (static)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onAction(StressTestAction.TriggerMemoryPressureAnr) },
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BitdriftColors.Error,
                    contentColor = Color.White,
                ),
        ) {
            Text("GC-Induced ANR (98% + allocations)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JankyFramesCard(onAction: (AppAction) -> Unit) {
    var selectedJankType by remember { mutableStateOf(JankType.SLOW) }

    StressTestCard(title = stringResource(id = R.string.janky_frames)) {
        Text(
            text = "Blocks main thread with Thread.sleep",
            style = MaterialTheme.typography.bodySmall,
            color = BitdriftColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selectedJankType.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Jank Type", color = BitdriftColors.TextSecondary) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                JankType.entries.forEach { jankType ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                jankType.displayName,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            selectedJankType = jankType
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onAction(StressTestAction.TriggerJankyFrames(selectedJankType)) },
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BitdriftColors.Warning,
                    contentColor = Color.Black,
                ),
        ) {
            Text("Trigger ${selectedJankType.displayName}")
        }
    }
}

@Composable
private fun StrictModeCard(onAction: (AppAction) -> Unit) {
    StressTestCard(title = stringResource(id = R.string.strict_mode)) {
        Text(
            text = "Performs disk I/O on main thread to trigger StrictMode violation",
            style = MaterialTheme.typography.bodySmall,
            color = BitdriftColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onAction(StressTestAction.TriggerStrictModeViolation) },
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BitdriftColors.Warning,
                    contentColor = Color.Black,
                ),
        ) {
            Text("Trigger StrictMode Violation")
        }
    }
}

@Composable
private fun StressTestCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )
            content()
        }
    }
}

