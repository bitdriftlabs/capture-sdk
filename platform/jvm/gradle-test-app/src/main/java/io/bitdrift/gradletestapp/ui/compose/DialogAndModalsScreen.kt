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
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogAndModalsScreen() {
    var showBottomSheet by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BitdriftColors.Background,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitdriftColors.Primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text("Show Dialog")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showBottomSheet = true },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitdriftColors.Primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text("Show Modal Bottom Sheet")
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text(
                        text = "Dialog",
                        color = BitdriftColors.TextPrimary,
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
                        Text("OK")
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
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Modal Bottom Sheet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = BitdriftColors.TextPrimary,
                    )

                    Text(
                        text = "This is a modal bottom sheet example. You can dismiss it by swiping down or tapping outside.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BitdriftColors.TextSecondary,
                    )

                    Button(
                        onClick = { showBottomSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BitdriftColors.Primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}
