// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.data.model.GlobalFieldEntry
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Network Testing Card component
 */
@Composable
fun GlobalFieldsCard(
    currentFields: List<GlobalFieldEntry>,
    addFieldAction: (String, String) -> Unit,
    removeFieldKeyAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyText by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }
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
                text = "Global Fields",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            currentFields.forEachIndexed { _, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = entry.key,
                        onValueChange = {},
                        label = { Text("Key") },
                        enabled = false,
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )

                    TextField(
                        value = entry.value,
                        onValueChange = {},
                        label = { Text("Value") },
                        enabled = false,
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = { removeFieldKeyAction(entry.key) },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = BitdriftColors.Error,
                                contentColor = BitdriftColors.TextBright,
                            ),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove",
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = keyText,
                    onValueChange = { keyText = it.trim() },
                    label = { Text("Key") },
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )

                TextField(
                    value = valueText,
                    onValueChange = { valueText = it.trim() },
                    label = { Text("Value") },
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )

                val canAdd = keyText.isNotBlank() && valueText.isNotBlank()
                IconButton(
                    onClick = {
                        addFieldAction(keyText, valueText)
                        keyText = ""
                        valueText = ""
                    },
                    enabled = canAdd,
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = BitdriftColors.Primary,
                            contentColor = Color.White,
                            disabledContainerColor = BitdriftColors.TextSecondary,
                            disabledContentColor = BitdriftColors.TextBright,
                        ),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                    )
                }
            }

        }
    }
}

@Preview
@Composable
fun AddGlobalFieldsCardPreview() {
    GlobalFieldsCard(
        currentFields = emptyList(),
        addFieldAction = { key: String, value: String -> },
        removeFieldKeyAction = { key: String -> },
        modifier = Modifier.fillMaxWidth(),
    )
}
