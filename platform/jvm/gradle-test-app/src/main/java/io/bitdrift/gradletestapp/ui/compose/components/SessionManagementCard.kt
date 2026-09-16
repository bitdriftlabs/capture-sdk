// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.ui.designsystem.BdReadOnlyField
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.theme.BdEyebrowStyle
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * Session Management Card component
 */
@Composable
fun SessionManagementCard(
    uiState: AppState,
    onStartNewSession: () -> Unit,
    onGenerateDeviceCode: () -> Unit,
    onCopySessionId: () -> Unit,
    onCopySessionUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = "Session Management",
        modifier = modifier,
    ) {
        BdReadOnlyField(
            label = "Session Strategy",
            value = uiState.config.sessionStrategy,
        )

        uiState.session.sessionId?.let { sessionId ->
            BdReadOnlyField(
                label = "Session ID",
                value = sessionId,
                singleLine = false,
                actionLabel = "Copy",
                onAction = onCopySessionId,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
        ) {
            BdSecondaryButton(
                text = "Copy URL",
                onClick = onCopySessionUrl,
                modifier = Modifier.weight(1f),
            )
            BdSecondaryButton(
                text = "New Session",
                onClick = onStartNewSession,
                modifier = Modifier.weight(1f),
            )
        }

        BdSecondaryButton(
            text = "Generate Device Code",
            onClick = onGenerateDeviceCode,
            modifier = Modifier.fillMaxWidth(),
        )

        val deviceCode = uiState.session.deviceCode
        if (deviceCode != null) {
            BdReadOnlyField(
                label = "Device Code",
                value = deviceCode,
            )
        } else {
            Text(
                text = "No Code Generated".uppercase(),
                style = BdEyebrowStyle,
                color = BitdriftColors.TextTertiary,
            )
        }
    }
}
