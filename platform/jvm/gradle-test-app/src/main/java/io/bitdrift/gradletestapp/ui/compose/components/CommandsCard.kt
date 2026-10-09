// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdStatusPill
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Live session commands card: registers or unregisters the sample commands
 */
@Composable
fun CommandsCard(
    registeredKeys: List<String>,
    onToggle: (registered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRegistered = registeredKeys.isNotEmpty()

    BdSectionCard(
        title = "Live Session Commands",
        modifier = modifier,
        subtitle = "Registered commands can be invoked from the live debugger",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BdSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Switch(
                    checked = isRegistered,
                    onCheckedChange = onToggle,
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
                    text = if (isRegistered) "Registered" else "Unregistered",
                    tone = if (isRegistered) BitdriftColors.Primary else BitdriftColors.TextSecondary,
                )
            }

            if (isRegistered) {
                Text(
                    text = registeredKeys.joinToString(),
                    color = BitdriftColors.TextSecondary,
                )
            }
        }
    }
}

@Preview
@Composable
fun CommandsCardPreview() {
    CommandsCard(
        registeredKeys = listOf("flip_flag", "memory_dump"),
        onToggle = {},
    )
}
