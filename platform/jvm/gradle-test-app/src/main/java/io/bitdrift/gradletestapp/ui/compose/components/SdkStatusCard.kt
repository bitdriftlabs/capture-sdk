// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.bitdrift.capture.InitializationState
import io.bitdrift.capture.SdkStatus
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.ui.designsystem.BdDetailRow
import io.bitdrift.gradletestapp.ui.designsystem.BdInsetPanel
import io.bitdrift.gradletestapp.ui.designsystem.BdPrimaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdStatusPill
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SDK Status Card component that displays the current SDK state
 */
@OptIn(ExperimentalBitdriftApi::class)
@Composable
fun SdkStatusCard(
    uiState: AppState,
    onInitializeSdk: () -> Unit,
    onCheckSdkState: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = "SDK State",
        modifier = modifier,
    ) {
        SdkStatusBadge(
            isInitialized = uiState.session.isSdkInitialized,
            apiKey = uiState.config.apiKey,
            apiUrl = uiState.config.apiUrl,
        )

        SdkStateSection(
            sdkStatus = uiState.session.sdkStatus,
            lastCheckTimeMs = uiState.session.lastConnectivityCheckTimeMs,
        )

        if (uiState.config.isDeferredStart && !uiState.session.isSdkInitialized) {
            BdPrimaryButton(
                text = "Start SDK",
                onClick = onInitializeSdk,
                enabled = !uiState.isLoading && uiState.config.apiKey.isNotEmpty() && uiState.config.apiUrl.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        BdPrimaryButton(
            text = "Check SDK State",
            onClick = onCheckSdkState,
            enabled = uiState.session.isSdkInitialized,
            modifier = Modifier.fillMaxWidth(),
        )

        BdSecondaryButton(
            text = "Settings",
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalBitdriftApi::class)
@Composable
private fun SdkStateSection(
    sdkStatus: SdkStatus?,
    lastCheckTimeMs: Long,
) {
    if (lastCheckTimeMs < 0) return

    BdInsetPanel(verticalArrangement = Arrangement.spacedBy(BdSpacing.sm)) {
        if (sdkStatus == null) {
            Text(
                text = "Not checked yet",
                style = MaterialTheme.typography.bodySmall,
                color = BitdriftColors.TextTertiary,
            )
        } else {
            val isRunning = sdkStatus.initializationState == InitializationState.RUNNING
            val stateLabel =
                when (sdkStatus.initializationState) {
                    InitializationState.NOT_STARTED -> "Not Started"
                    InitializationState.LOADED -> "Loaded"
                    InitializationState.RUNNING -> "Running"
                    InitializationState.DISABLED -> "Disabled"
                }

            Text(
                text = stateLabel,
                style = MaterialTheme.typography.titleSmall,
                color = if (isRunning) BitdriftColors.PrimaryBright else BitdriftColors.TextTertiary,
            )

            BdDetailRow(
                label = "Last handshake",
                value = sdkStatus.lastHandshakeTimeMs?.let(::formatEpochMs) ?: "none yet",
                valueColor =
                    if (sdkStatus.lastHandshakeTimeMs == null) {
                        BitdriftColors.TextTertiary
                    } else {
                        BitdriftColors.TextPrimary
                    },
            )

            BdDetailRow(
                label = "Last config delivery",
                value = sdkStatus.lastConfigDeliveryTimeMs?.let(::formatEpochMs) ?: "none yet",
                valueColor =
                    if (sdkStatus.lastConfigDeliveryTimeMs == null) {
                        BitdriftColors.TextTertiary
                    } else {
                        BitdriftColors.TextPrimary
                    },
            )
        }

        BdDetailRow(
            label = "Checked at",
            value = formatEpochMs(lastCheckTimeMs),
            valueColor = BitdriftColors.TextTertiary,
        )
    }
}

@Composable
private fun SdkStatusBadge(
    isInitialized: Boolean,
    apiKey: String,
    apiUrl: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BdSpacing.sm),
    ) {
        BdStatusPill(
            text = if (isInitialized) "Initialized" else "Not Initialized",
            tone = if (isInitialized) BitdriftColors.Primary else BitdriftColors.Error,
            icon = if (isInitialized) Icons.Filled.CheckCircle else Icons.Filled.Close,
        )

        if (!isInitialized) {
            Text(
                text =
                    when {
                        apiKey.isBlank() -> "Missing API key"
                        apiUrl.isBlank() -> "Missing API URL"
                        else -> "Click Start SDK to initialize"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = BitdriftColors.Error,
            )
        }
    }
}

private fun formatEpochMs(epochMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
