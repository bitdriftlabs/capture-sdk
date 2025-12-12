// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.NavigationAction
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationCard(onAction: (AppAction) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = BitdriftColors.BackgroundPaper),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(width = 1.dp, color = BitdriftColors.Border.copy(alpha = 0.3f)),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.navigation),
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onAction(NavigationAction.NavigateToWebView) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text(stringResource(id = R.string.navigate_to_web_view), maxLines = 1, softWrap = false) }

                OutlinedButton(
                    onClick = { onAction(NavigationAction.NavigateToCompose) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text(stringResource(id = R.string.navigate_second), maxLines = 1, softWrap = false) }

                OutlinedButton(
                    onClick = { onAction(NavigationAction.NavigateToXml) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text(stringResource(id = R.string.navigate_to_xml_view), maxLines = 1, softWrap = false) }

                OutlinedButton(
                    onClick = { onAction(NavigationAction.NavigateToDialogAndModals) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text(stringResource(id = R.string.navigate_to_modal_bottom_sheet), maxLines = 1, softWrap = false) }

                OutlinedButton(
                    onClick = { onAction(NavigationAction.InvokeService) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text("Invoke Service", maxLines = 1, softWrap = false) }
            }
        }
    }
}
