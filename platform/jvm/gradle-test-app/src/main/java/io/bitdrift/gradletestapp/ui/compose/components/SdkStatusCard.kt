// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.bitdrift.capture.InitializationState
import io.bitdrift.capture.SdkStatus
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.gradletestapp.data.model.AppState
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
                text = "SDK State",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

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
                Button(
                    onClick = onInitializeSdk,
                    enabled = !uiState.isLoading && uiState.config.apiKey.isNotEmpty() && uiState.config.apiUrl.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitdriftColors.Primary,
                            contentColor = BitdriftColors.TextBright,
                            disabledContainerColor = BitdriftColors.TextTertiary,
                            disabledContentColor = BitdriftColors.TextSecondary,
                        ),
                ) {
                    Text("Start SDK")
                }
            }

            Button(
                onClick = onCheckSdkState,
                enabled = uiState.session.isSdkInitialized,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitdriftColors.Primary,
                        contentColor = Color.White,
                    ),
            ) {
                Text("Check SDK State")
            }

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = BitdriftColors.TextPrimary,
                    ),
            ) {
                Text("Settings")
            }
        }
    }
}

@OptIn(ExperimentalBitdriftApi::class)
@Composable
private fun SdkStateSection(
    sdkStatus: SdkStatus?,
    lastCheckTimeMs: Long,
) {
    if (lastCheckTimeMs < 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = BitdriftColors.BackgroundPaper,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = BitdriftColors.Border.copy(alpha = 0.2f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (sdkStatus == null) {
                Text(
                    text = "Not checked yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = BitdriftColors.TextTertiary,
                )
            } else {
                val stateLabel = when (sdkStatus.initializationState) {
                    InitializationState.NOT_STARTED -> "Not Started"
                    InitializationState.LOADED -> "Loaded"
                    InitializationState.RUNNING -> "Running"
                    InitializationState.DISABLED -> "Disabled"
                }

                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sdkStatus.initializationState == InitializationState.RUNNING) {
                        BitdriftColors.Primary
                    } else {
                        BitdriftColors.TextTertiary
                    },
                )

                sdkStatus.lastHandshakeTimeMs?.let { timeMs ->
                    Text(
                        text = "Last handshake: ${formatEpochMs(timeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BitdriftColors.TextSecondary,
                    )
                }

                sdkStatus.lastConfigDeliveryTimeMs?.let { timeMs ->
                    Text(
                        text = "Last config delivery: ${formatEpochMs(timeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BitdriftColors.TextSecondary,
                    )
                }

                if (sdkStatus.lastHandshakeTimeMs == null) {
                    Text(
                        text = "No handshake yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = BitdriftColors.TextTertiary,
                    )
                }

                if (sdkStatus.lastHandshakeTimeMs == null && sdkStatus.lastConfigDeliveryTimeMs == null) {
                    Text(
                        text = "No config delivery yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = BitdriftColors.TextTertiary,
                    )
                }
            }

            Text(
                text = "Checked at: ${formatEpochMs(lastCheckTimeMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = BitdriftColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun SdkStatusBadge(
    isInitialized: Boolean,
    apiKey: String,
    apiUrl: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isInitialized) {
                        BitdriftColors.Primary.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    },
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (isInitialized) {
                        BitdriftColors.Primary.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isInitialized) Icons.Filled.CheckCircle else Icons.Filled.Close,
                contentDescription = "SDK State",
                modifier = Modifier.size(24.dp),
                tint = if (isInitialized) BitdriftColors.Primary else MaterialTheme.colorScheme.error,
            )
            Text(
                text = if (isInitialized) "Initialized" else "Not Initialized",
                style = MaterialTheme.typography.titleSmall,
                color = if (isInitialized) BitdriftColors.Primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!isInitialized) {
            Text(
                text =
                    when {
                        apiKey.isBlank() -> "Missing API key"
                        apiUrl.isBlank() -> "Missing API URL"
                        else -> "Click Start SDK to initialize"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

private fun formatEpochMs(epochMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
