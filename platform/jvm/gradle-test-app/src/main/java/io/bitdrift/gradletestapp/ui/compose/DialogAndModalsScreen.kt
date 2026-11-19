// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.ui.designsystem.BdPrimaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogAndModalsScreen() {
    var showBottomSheet by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(containerColor = BitdriftColors.Background) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(BdSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BdSpacing.lg, Alignment.CenterVertically),
        ) {
            BdPrimaryButton(
                text = "Show Dialog",
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )

            BdSecondaryButton(
                text = "Show Modal Bottom Sheet",
                onClick = { showBottomSheet = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text(
                        text = "Dialog",
                        color = BitdriftColors.TextBright,
                    )
                },
                text = {
                    Text(
                        text = "This is a generic dialog example.",
                        color = BitdriftColors.TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("OK", color = BitdriftColors.PrimaryBright)
                    }
                },
                containerColor = BitdriftColors.BackgroundPaper,
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                containerColor = BitdriftColors.BackgroundPaper,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(BdSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(BdSpacing.lg),
                ) {
                    Text(
                        text = "Modal Bottom Sheet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = BitdriftColors.TextBright,
                    )

                    Text(
                        text =
                            "This is a modal bottom sheet example. You can dismiss it by swiping " +
                                "down or tapping outside.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BitdriftColors.TextSecondary,
                    )

                    BdPrimaryButton(
                        text = "Dismiss",
                        onClick = { showBottomSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
