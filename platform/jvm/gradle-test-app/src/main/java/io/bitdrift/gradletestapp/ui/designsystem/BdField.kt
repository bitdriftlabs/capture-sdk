// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.ui.theme.BdShape
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/** Shared text field colours. */
@Composable
fun bdFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = BitdriftColors.TextBright,
        unfocusedTextColor = BitdriftColors.TextPrimary,
        disabledTextColor = BitdriftColors.TextTertiary,
        focusedBorderColor = BitdriftColors.Primary,
        unfocusedBorderColor = BitdriftColors.Border,
        disabledBorderColor = BitdriftColors.Border,
        focusedLabelColor = BitdriftColors.Primary,
        unfocusedLabelColor = BitdriftColors.TextSecondary,
        disabledLabelColor = BitdriftColors.TextTertiary,
        cursorColor = BitdriftColors.PrimaryBright,
        focusedContainerColor = BitdriftColors.Background.copy(alpha = 0.6f),
        unfocusedContainerColor = BitdriftColors.Background.copy(alpha = 0.4f),
        disabledContainerColor = BitdriftColors.Background.copy(alpha = 0.3f),
        selectionColors =
            TextSelectionColors(
                handleColor = BitdriftColors.PrimaryBright,
                backgroundColor = BitdriftColors.Primary.copy(alpha = 0.3f),
            ),
    )

@Composable
fun BdTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        shape = BdShape.Field,
        colors = bdFieldColors(),
    )
}

/** Read-only field, optionally with a trailing action. */
@Composable
fun BdReadOnlyField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        readOnly = true,
        singleLine = singleLine,
        shape = BdShape.Field,
        colors = bdFieldColors(),
        trailingIcon =
            if (actionLabel != null && onAction != null) {
                {
                    TextButton(onClick = onAction) {
                        Text(actionLabel, color = BitdriftColors.PrimaryBright)
                    }
                }
            } else {
                null
            },
    )
}

/** Read-only dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> BdDropdownField(
    label: String,
    selected: T,
    options: List<T>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            shape = BdShape.Field,
            colors = bdFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = BitdriftColors.BackgroundElevated,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor =
                                if (option == selected) {
                                    BitdriftColors.PrimaryBright
                                } else {
                                    BitdriftColors.TextPrimary
                                },
                        ),
                )
            }
        }
    }
}
