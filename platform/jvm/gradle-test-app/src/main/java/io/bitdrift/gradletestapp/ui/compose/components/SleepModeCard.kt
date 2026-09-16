// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdStatusPill
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Sleep Mode controls card: toggle + state pill
 */
@Composable
fun SleepModeCard(
    uiState: AppState,
    onToggle: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isChecked by remember(uiState.config.isSleepModeEnabled) { mutableStateOf(uiState.config.isSleepModeEnabled) }

    BdSectionCard(
        title = "Sleep Mode",
        modifier = modifier,
        subtitle =
            if (uiState.session.isSdkInitialized) {
                null
            } else {
                "Initialize SDK to toggle sleep mode"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Switch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    if (uiState.session.isSdkInitialized) {
                        onToggle(it)
                    }
                },
                enabled = uiState.session.isSdkInitialized,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = BitdriftColors.Background,
                        checkedTrackColor = BitdriftColors.Primary,
                        checkedBorderColor = BitdriftColors.Primary,
                        uncheckedThumbColor = BitdriftColors.TextTertiary,
                        uncheckedTrackColor = BitdriftColors.BackgroundElevated,
                        uncheckedBorderColor = BitdriftColors.Border,
                    ),
            )

            BdStatusPill(
                text = if (isChecked) "Enabled" else "Disabled",
                tone = if (isChecked) BitdriftColors.Primary else BitdriftColors.TextSecondary,
            )
        }
    }
}

@Preview
@Composable
fun SleepModeCardPreview() {
    SleepModeCard(
        AppState(),
        {},
    )
}
