// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.ui.designsystem.BdDetailRow
import io.bitdrift.gradletestapp.ui.designsystem.BdInsetPanel
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@Composable
fun TracingStatusCard(
    currentIsTracingActive: () -> Boolean?,
    modifier: Modifier = Modifier,
) {
    var tracingStatus by remember { mutableStateOf<Boolean?>(null) }

    BdSectionCard(
        title = "Tracing Status",
        modifier = modifier,
    ) {
        BdInsetPanel {
            BdDetailRow(
                label = "Tracing active",
                value = tracingStatus?.toString() ?: "Unknown",
                valueColor =
                    when (tracingStatus) {
                        true -> BitdriftColors.PrimaryBright
                        false -> BitdriftColors.Error
                        null -> BitdriftColors.TextTertiary
                    },
            )
        }

        BdSecondaryButton(
            text = "Check Tracing Active",
            onClick = { tracingStatus = currentIsTracingActive() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
