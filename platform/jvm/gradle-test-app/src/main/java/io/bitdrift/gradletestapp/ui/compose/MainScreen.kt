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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.AppState
import io.bitdrift.gradletestapp.data.model.ClearError
import io.bitdrift.gradletestapp.data.model.ConfigAction
import io.bitdrift.gradletestapp.data.model.DiagnosticsAction
import io.bitdrift.gradletestapp.data.model.FeatureFlagsTestAction
import io.bitdrift.gradletestapp.data.model.GlobalFieldAction
import io.bitdrift.gradletestapp.data.model.NetworkTestAction
import io.bitdrift.gradletestapp.data.model.SessionAction
import io.bitdrift.gradletestapp.ui.compose.components.GlobalFieldsCard
import io.bitdrift.gradletestapp.ui.compose.components.FeatureFlagsTestingCard
import io.bitdrift.gradletestapp.ui.compose.components.NavigationCard
import io.bitdrift.gradletestapp.ui.compose.components.NetworkTestingCard
import io.bitdrift.gradletestapp.ui.compose.components.SdkStatusCard
import io.bitdrift.gradletestapp.ui.compose.components.SessionManagementCard
import io.bitdrift.gradletestapp.ui.compose.components.SleepModeCard
import io.bitdrift.gradletestapp.ui.compose.components.TestingToolsCard
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

private enum class BottomNavTab(
    val label: String,
    val screenName: String,
    val icon: ImageVector,
) {
    HOME("Home", "home_tab", Icons.Default.Home),
    SDK_APIS("SDK APIs", "sdk_apis_tab", Icons.Default.PlayArrow),
    STRESS_TESTS("Stress", "stress_tests_tab", Icons.Default.Warning),
    NAVIGATE("Navigate", "navigate_tab", Icons.Default.Menu),
    SETTINGS("Settings", "settings_tab", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: AppState,
    onAction: (AppAction) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(BottomNavTab.HOME.ordinal) }
    val currentTab = BottomNavTab.entries[selectedTab]

    LaunchedEffect(currentTab) {
        Logger.logScreenView(currentTab.screenName)
    }

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
        bottomBar = {
            NavigationBar(
                containerColor = BitdriftColors.BackgroundPaper,
            ) {
                BottomNavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { selectedTab = tab.ordinal },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BitdriftColors.Primary,
                            selectedTextColor = BitdriftColors.Primary,
                            unselectedIconColor = BitdriftColors.TextSecondary,
                            unselectedTextColor = BitdriftColors.TextSecondary,
                            indicatorColor = BitdriftColors.Primary.copy(alpha = 0.1f),
                        ),
                    )
                }
            }
        },
        containerColor = BitdriftColors.Background,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            ErrorBanner(
                error = uiState.error,
                onDismiss = { onAction(ClearError) },
            )

            when (currentTab) {
                BottomNavTab.HOME -> HomeTabContent(
                    uiState = uiState,
                    onAction = onAction,
                    clipboardManager = clipboardManager,
                    onOpenSettings = { selectedTab = BottomNavTab.SETTINGS.ordinal },
                )
                BottomNavTab.SDK_APIS -> SdkApisTabContent(
                    uiState = uiState,
                    onAction = onAction,
                    context = context,
                )
                BottomNavTab.STRESS_TESTS -> StressTestsTabContent(
                    onAction = onAction,
                )
                BottomNavTab.NAVIGATE -> NavigateTabContent(
                    onAction = onAction,
                )
                BottomNavTab.SETTINGS -> SettingsTabContent()
            }

            if (uiState.isLoading) {
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

@Composable
private fun ErrorBanner(
    error: String?,
    onDismiss: () -> Unit,
) {
    error?.let {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    uiState: AppState,
    onAction: (AppAction) -> Unit,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            SdkStatusCard(
                uiState = uiState,
                onInitializeSdk = { onAction(ConfigAction.InitializeSdk) },
                onOpenSettings = onOpenSettings,
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
    }
}

@Composable
private fun SdkApisTabContent(
    uiState: AppState,
    onAction: (AppAction) -> Unit,
    context: android.content.Context,
) {
    val listState = rememberLazyListState()
    val toasterText = stringResource(
        R.string.log_message_toast,
        uiState.config.selectedLogLevel,
    )

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            TestingToolsCard(
                uiState = uiState,
                onLogLevelChange = { onAction(ConfigAction.UpdateLogLevel(it)) },
                onAppExitReasonChange = { onAction(DiagnosticsAction.UpdateAppExitReason(it)) },
                onLogMessage = {
                    onAction(DiagnosticsAction.LogMessage)
                    Toast.makeText(context, toasterText, Toast.LENGTH_SHORT).show()
                },
                onAction = onAction,
            )
        }
        item {
            GlobalFieldsCard(
                currentFields = uiState.globalFields,
                addFieldAction = { key: String, value: String -> onAction(GlobalFieldAction.AddFieldAction(key, value)) },
                removeFieldKeyAction = { key: String -> onAction(GlobalFieldAction.RemoveFieldKey(key)) },
            )
        }
        item {
            SleepModeCard(
                uiState = uiState,
                onToggle = { enabled -> onAction(ConfigAction.SetSleepModeEnabled(enabled)) },
            )
        }
        item {
            FeatureFlagsTestingCard(
                onAddVariantFlag = { value -> onAction(FeatureFlagsTestAction.AddVariantFlag(value)) },
                onAddManyFeatureFlags = { onAction(FeatureFlagsTestAction.AddManyFeatureFlags) },
            )
        }
        item {
            NetworkTestingCard(
                onOkHttpManualRequest = { onAction(NetworkTestAction.PerformOkHttpRequestManual) },
                onOkHttpAutoRequest = { onAction(NetworkTestAction.PerformOkHttpRequestAutomatic) },
                onGraphQlRequest = { onAction(NetworkTestAction.PerformGraphQlRequest) },
                onRetrofitRequest = { onAction(NetworkTestAction.PerformRetrofitRequest) },
            )
        }
    }
}

@Composable
private fun StressTestsTabContent(
    onAction: (AppAction) -> Unit,
) {
    StressTestScreen(
        onAction = onAction,
        onNavigateBack = {},
    )
}

@Composable
private fun NavigateTabContent(
    onAction: (AppAction) -> Unit,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            NavigationCard(onAction = onAction)
        }
    }
}

private const val SETTINGS_FRAGMENT_TAG = "settings_fragment"
private const val SETTINGS_CONTAINER_ID = 0x7f0b0001

@Composable
private fun SettingsTabContent() {
    val context = LocalContext.current
    val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager

    DisposableEffect(Unit) {
        onDispose {
            fragmentManager?.let { fm ->
                val fragment = fm.findFragmentByTag(SETTINGS_FRAGMENT_TAG)
                if (fragment != null) {
                    fm.commit { remove(fragment) }
                }
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = SETTINGS_CONTAINER_ID
            }
        },
        update = { container ->
            fragmentManager?.let { fm ->
                val existingFragment = fm.findFragmentByTag(SETTINGS_FRAGMENT_TAG)
                if (existingFragment == null) {
                    fm.commit {
                        replace(container.id, ConfigurationSettingsFragment(), SETTINGS_FRAGMENT_TAG)
                    }
                }
            }
        },
    )
}
