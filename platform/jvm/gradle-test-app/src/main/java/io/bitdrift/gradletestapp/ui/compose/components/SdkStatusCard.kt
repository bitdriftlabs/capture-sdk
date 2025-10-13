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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/**
 * SDK Status Card component that displays the current SDK state
 */
@Composable
fun SdkStatusCard(
    uiState: AppState,
    onInitializeSdk: () -> Unit,
    onNavigateToConfig: () -> Unit,
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
                text = "SDK Status",
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            SdkStatusBadge(
                isInitialized = uiState.session.isSdkInitialized,
                apiKey = uiState.config.apiKey,
                apiUrl = uiState.config.apiUrl,
            )

            val isValid = uiState.session.isDeviceCodeValid
            val error = uiState.session.deviceCodeError
            val deviceStatusText =
                if (isValid) {
                    stringResource(id = R.string.device_code_valid)
                } else {
                    error ?: stringResource(id = R.string.device_code_invalid)
                }
            Text(
                text = deviceStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isValid) BitdriftColors.Primary else BitdriftColors.Error,
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

            OutlinedButton(
                onClick = onNavigateToConfig,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = BitdriftColors.TextPrimary,
                    ),
                border =
                    ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.dp,
                    ),
            ) {
                Text("Settings")
            }
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
                imageVector = if (isInitialized) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = "SDK Status",
                modifier = Modifier.size(24.dp),
                tint = if (isInitialized) BitdriftColors.Primary else MaterialTheme.colorScheme.error,
            )
            Text(
                text = if (isInitialized) "Started" else "Not Started",
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
