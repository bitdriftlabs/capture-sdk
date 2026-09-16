// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.bitdrift.gradletestapp.data.model.GlobalFieldEntry
import io.bitdrift.gradletestapp.ui.designsystem.BdIconButton
import io.bitdrift.gradletestapp.ui.designsystem.BdReadOnlyField
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdTextField
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Global fields card
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

    BdSectionCard(
        title = "Global Fields",
        modifier = modifier,
    ) {
        currentFields.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BdReadOnlyField(
                    label = "Key",
                    value = entry.key,
                    modifier = Modifier.weight(1f),
                )
                BdReadOnlyField(
                    label = "Value",
                    value = entry.value,
                    modifier = Modifier.weight(1f),
                )
                BdIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Remove",
                    onClick = { removeFieldKeyAction(entry.key) },
                    accent = BitdriftColors.Error,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BdTextField(
                label = "Key",
                value = keyText,
                onValueChange = { keyText = it.trim() },
                modifier = Modifier.weight(1f),
            )
            BdTextField(
                label = "Value",
                value = valueText,
                onValueChange = { valueText = it.trim() },
                modifier = Modifier.weight(1f),
            )
            BdIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add",
                onClick = {
                    addFieldAction(keyText, valueText)
                    keyText = ""
                    valueText = ""
                },
                enabled = keyText.isNotBlank() && valueText.isNotBlank(),
            )
        }
    }
}

@Preview
@Composable
fun AddGlobalFieldsCardPreview() {
    GlobalFieldsCard(
        currentFields = emptyList(),
        addFieldAction = { _, _ -> },
        removeFieldKeyAction = { },
    )
}
