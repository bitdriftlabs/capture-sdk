// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.AppExitReason
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.data.model.DiagnosticsAction
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdTintedButton
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@Composable
fun AppTerminationsCard(
    uiState: AppState,
    onAppExitReasonChange: (AppExitReason) -> Unit,
    onAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = "App Terminations",
        subtitle = "Choose a specific termination reason for force-exit testing.",
        modifier = modifier,
    ) {
        AppExitReasonSelector(
            selectedAppExitReason = uiState.diagnostics.selectedAppExitReason,
            onAppExitReasonChange = onAppExitReasonChange,
        )

        BdTintedButton(
            text = "Force App Exit",
            accent = BitdriftColors.Error,
            onClick = { onAction(DiagnosticsAction.ForceAppExit) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun FatalIssuesCard(
    onAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = "Trigger a Random Fatal Issue",
        modifier = modifier,
    ) {
        BdTintedButton(
            text = "Native Crash",
            accent = BitdriftColors.CrashNative,
            onClick = { onAction(DiagnosticsAction.TriggerRandomNativeCrash) },
            modifier = Modifier.fillMaxWidth(),
        )

        BdTintedButton(
            text = "JVM crash",
            accent = BitdriftColors.CrashJvm,
            onClick = { onAction(DiagnosticsAction.TriggerRandomJvmCrash) },
            modifier = Modifier.fillMaxWidth(),
        )

        BdTintedButton(
            text = "ANR",
            accent = BitdriftColors.CrashAnr,
            onClick = { onAction(DiagnosticsAction.TriggerRandomAnrCrash) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
