// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.ui.theme.BdShape
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/** Shared dialog shell. */
@Composable
fun BdAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "OK",
    dismissText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = BitdriftColors.BackgroundPaper,
        shape = BdShape.Card,
        titleContentColor = BitdriftColors.TextBright,
        textContentColor = BitdriftColors.TextPrimary,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextBright,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(BdSpacing.md),
                content = content,
            )
        },
        confirmButton = {
            BdPrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                size = BdButtonSize.Compact,
            )
        },
        dismissButton =
            if (dismissText != null) {
                {
                    BdSecondaryButton(
                        text = dismissText,
                        onClick = onDismiss,
                        size = BdButtonSize.Compact,
                    )
                }
            } else {
                null
            },
    )
}
