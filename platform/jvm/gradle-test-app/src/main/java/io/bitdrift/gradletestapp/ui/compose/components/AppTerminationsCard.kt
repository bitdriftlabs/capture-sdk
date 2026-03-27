// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.data.model.DiagnosticsAction
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@Composable
fun AppTerminationsCard(
    uiState: AppState,
    onAppExitReasonChange: (io.bitdrift.gradletestapp.data.model.AppExitReason) -> Unit,
    onAction: (AppAction) -> Unit,
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
                text = "App Terminations",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            AppExitReasonSelector(
                selectedAppExitReason = uiState.diagnostics.selectedAppExitReason,
                onAppExitReasonChange = onAppExitReasonChange,
            )

            TerminationButton(
                text = "Force App Exit",
                onClick = { onAction(DiagnosticsAction.ForceAppExit) },
                containerColor = BitdriftColors.Error,
            )

            Text(
                text = "Choose a specific termination reason for force-exit testing.",
                style = MaterialTheme.typography.bodySmall,
                color = BitdriftColors.TextSecondary,
            )
        }
    }
}

@Composable
fun FatalIssuesCard(
    onAction: (AppAction) -> Unit,
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
                text = "Trigger a Random Fatal Issue",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            TerminationButton(
                text = "Native Crash",
                onClick = { onAction(DiagnosticsAction.TriggerRandomNativeCrash) },
                containerColor = BitdriftColors.CrashNative,
            )

            TerminationButton(
                text = "JVM crash",
                onClick = { onAction(DiagnosticsAction.TriggerRandomJvmCrash) },
                containerColor = BitdriftColors.CrashJvm,
            )

            TerminationButton(
                text = "ANR crash",
                onClick = { onAction(DiagnosticsAction.TriggerRandomAnrCrash) },
                containerColor = BitdriftColors.CrashAnr,
            )
        }
    }
}

@Composable
private fun TerminationButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = BitdriftColors.TextBright,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = text, color = Color.White)
    }
}
