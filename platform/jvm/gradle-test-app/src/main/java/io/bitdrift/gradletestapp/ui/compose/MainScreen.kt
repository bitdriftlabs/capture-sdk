// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.data.model.ClearError
import io.bitdrift.gradletestapp.data.model.ConfigAction
import io.bitdrift.gradletestapp.data.model.DiagnosticsAction
import io.bitdrift.gradletestapp.data.model.FeatureFlagsTestAction
import io.bitdrift.gradletestapp.data.model.NavigationAction
import io.bitdrift.gradletestapp.data.model.NetworkTestAction
import io.bitdrift.gradletestapp.data.model.SessionAction
import io.bitdrift.gradletestapp.ui.compose.components.FeatureFlagsTestingCard
import io.bitdrift.gradletestapp.ui.compose.components.NavigationCard
import io.bitdrift.gradletestapp.ui.compose.components.StressTestCard
import io.bitdrift.gradletestapp.ui.compose.components.NetworkTestingCard
import io.bitdrift.gradletestapp.ui.compose.components.SdkStatusCard
import io.bitdrift.gradletestapp.ui.compose.components.SessionManagementCard
import io.bitdrift.gradletestapp.ui.compose.components.SleepModeCard
import io.bitdrift.gradletestapp.ui.compose.components.TestingToolsCard
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: AppState,
    onAction: (AppAction) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bitdrift_logo_final),
                            contentDescription = stringResource(id = R.string.app_name),
                            modifier = Modifier.size(24.dp),
                            tint = BitdriftColors.Primary,
                        )
                        Text(
                            text = stringResource(id = R.string.first_fragment_label),
                            color = BitdriftColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = BitdriftColors.BackgroundPaper,
                        titleContentColor = BitdriftColors.TextPrimary,
                    ),
            )
        },
        containerColor = BitdriftColors.Background,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onAction(ClearError) },
                        ) {
                            Text(stringResource(id = android.R.string.cancel))
                        }
                    }
                }
            }

            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item {
                    SdkStatusCard(
                        uiState = uiState,
                        onInitializeSdk = { onAction(ConfigAction.InitializeSdk) },
                        onAction = onAction,
                    )
                }
                item {
                    SessionManagementCard(
                        uiState = uiState,
                        onStartNewSession = { onAction(SessionAction.StartNewSession) },
                        onGenerateDeviceCode = { onAction(SessionAction.GenerateDeviceCode) },
                        onCopySessionUrl = {
                            onAction(SessionAction.CopySessionUrl)
                            uiState.session.sessionUrl?.let { url ->
                                clipboardManager.setText(AnnotatedString(url))
                            }
                        },
                    )
                }
                item {
                    TestingToolsCard(
                        uiState = uiState,
                        onLogLevelChange = { onAction(ConfigAction.UpdateLogLevel(it)) },
                        onAppExitReasonChange = {
                            onAction(DiagnosticsAction.UpdateAppExitReason(it))
                        },
                        onLogMessage = {
                            onAction(DiagnosticsAction.LogMessage)

                            val toasterText =
                                context.getString(
                                    R.string.log_message_toast,
                                    uiState.config.selectedLogLevel,
                                )
                            Toast.makeText(context, toasterText, Toast.LENGTH_SHORT).show()
                        },
                        onAction = onAction,
                    )
                }
                item {
                    StressTestCard(
                        onNavigateToStressTest = { onAction(NavigationAction.NavigateToStressTest) },
                    )
                }
                item {
                    SleepModeCard(
                        uiState = uiState,
                        onToggle = { enabled -> onAction(ConfigAction.SetSleepModeEnabled(enabled)) },
                    )
                }
                item {
                    NetworkTestingCard(
                        onOkHttpRequest = {
                            onAction(NetworkTestAction.PerformOkHttpRequest)
                        },
                        onGraphQlRequest = {
                            onAction(NetworkTestAction.PerformGraphQlRequest)
                        },
                        onRetrofitRequest = {
                            onAction(NetworkTestAction.PerformRetrofitRequest)
                        },
                    )
                }
                item {
                    FeatureFlagsTestingCard(
                        onAddOneFeatureFlag = {
                            onAction(FeatureFlagsTestAction.AddOneFeatureFlag)
                        },
                        onAddManyFeatureFlags = {
                            onAction(FeatureFlagsTestAction.AddManyFeatureFlags)
                        },
                        onClearFeatureFlags = {
                            onAction(FeatureFlagsTestAction.ClearFeatureFlags)
                        },
                    )
                }
                item {
                    NavigationCard(
                        onAction = onAction,
                    )
                }
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
