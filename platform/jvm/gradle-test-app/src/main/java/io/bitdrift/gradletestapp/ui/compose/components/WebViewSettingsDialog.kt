// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.bitdrift.capture.webview.WebViewInstrumentationMode
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors
import io.bitdrift.gradletestapp.ui.theme.BitdriftTheme

class WebViewSettingsDialog(
    private val sharedPreferences: SharedPreferences,
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setContent {
                BitdriftTheme {
                    WebViewSettingsDialog(
                        onDismiss = { dismiss() },
                        sharedPreferences = sharedPreferences,
                    )
                }
            }
        }

    private fun SharedPreferences.getStateOf(keyName: String): MutableState<Boolean> =
        mutableStateOf(getBoolean(keyName, false))

    private fun SharedPreferences.getModeState(): MutableState<WebViewInstrumentationMode> {
        val modeName = getString(WEBVIEW_INSTRUMENTATION_MODE_KEY, WebViewInstrumentationMode.NATIVE_ONLY.name)
        val mode = runCatching {
            WebViewInstrumentationMode.valueOf(modeName ?: WebViewInstrumentationMode.NATIVE_ONLY.name)
        }.getOrDefault(WebViewInstrumentationMode.NATIVE_ONLY)
        return mutableStateOf(mode)
    }

    @SuppressLint("NotConstructor")
    @Composable
    fun WebViewSettingsDialog(
        onDismiss: () -> Unit,
        sharedPreferences: SharedPreferences,
    ) {
        var monitoringEnabled by remember { sharedPreferences.getStateOf(WEBVIEW_MONITORING_ENABLED_KEY) }
        var instrumentationMode by remember { sharedPreferences.getModeState() }
        var captureConsoleLogs by remember { sharedPreferences.getStateOf(WEBVIEW_ENABLE_CONSOLE_LOGS_KEY) }
        var captureErrors by remember { sharedPreferences.getStateOf(WEBVIEW_ENABLE_ERRORS_KEY) }
        var captureNetworkRequests by remember { sharedPreferences.getStateOf(WEBVIEW_ENABLE_NETWORK_REQUESTS_KEY) }
        var captureNavigationEvents by remember { sharedPreferences.getStateOf(WEBVIEW_ENABLE_NAVIGATION_EVENTS_KEY) }
        var capturePageViews by remember { sharedPreferences.getStateOf(WEBVIEW_ENABLE_PAGE_VIEWS_KEY) }
        var captureWebVitals by remember { sharedPreferences.getStateOf(WEBVIEW_ENABLE_WEB_VITALS_KEY) }
        var captureLongTasks by remember { sharedPreferences.getStateOf(WEBVIEW_ENABLE_LONG_TASKS_KEY) }
        var captureUserInteractions by remember { sharedPreferences.getStateOf(WEBVIEW_ENABLE_USER_INTERACTIONS_KEY) }

        val isFullJsMode = instrumentationMode == WebViewInstrumentationMode.JAVASCRIPT_BRIDGE

        fun persistSettingsAndDismiss() {
            with(sharedPreferences.edit()) {
                putBoolean(WEBVIEW_MONITORING_ENABLED_KEY, monitoringEnabled)
                putString(WEBVIEW_INSTRUMENTATION_MODE_KEY, instrumentationMode.name)
                putBoolean(WEBVIEW_ENABLE_CONSOLE_LOGS_KEY, captureConsoleLogs)
                putBoolean(WEBVIEW_ENABLE_ERRORS_KEY, captureErrors)
                putBoolean(WEBVIEW_ENABLE_NETWORK_REQUESTS_KEY, captureNetworkRequests)
                putBoolean(WEBVIEW_ENABLE_NAVIGATION_EVENTS_KEY, captureNavigationEvents)
                putBoolean(WEBVIEW_ENABLE_PAGE_VIEWS_KEY, capturePageViews)
                putBoolean(WEBVIEW_ENABLE_WEB_VITALS_KEY, captureWebVitals)
                putBoolean(WEBVIEW_ENABLE_LONG_TASKS_KEY, captureLongTasks)
                putBoolean(WEBVIEW_ENABLE_USER_INTERACTIONS_KEY, captureUserInteractions)
                apply()
            }
            onDismiss()
        }

        AlertDialog(
            onDismissRequest = { onDismiss() },
            containerColor = BitdriftColors.BackgroundPaper,
            title = {
                Text(
                    text = "WebView Monitoring",
                    fontSize = 20.sp,
                    color = BitdriftColors.TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    SettingsSwitchRow(
                        label = "Enable Monitoring",
                        description = "Enable WebView instrumentation",
                        checked = monitoringEnabled,
                        onCheckedChange = { monitoringEnabled = it },
                    )

                    if (monitoringEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Instrumentation Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BitdriftColors.TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        InstrumentationModeSelector(
                            selectedMode = instrumentationMode,
                            onModeSelected = { instrumentationMode = it },
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Capture Options",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BitdriftColors.TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        SettingsSwitchRow(
                            label = "Page Views",
                            description = "Capture page view spans",
                            checked = capturePageViews,
                            onCheckedChange = { capturePageViews = it },
                        )

                        SettingsSwitchRow(
                            label = "Errors",
                            description = if (isFullJsMode) "Capture JS errors and resource failures" else "Capture HTTP/SSL errors",
                            checked = captureErrors,
                            onCheckedChange = { captureErrors = it },
                        )

                        SettingsSwitchRow(
                            label = "Network Requests",
                            description = if (isFullJsMode) "Capture fetch/XHR with timing" else "Capture resource load events",
                            checked = captureNetworkRequests,
                            onCheckedChange = { captureNetworkRequests = it },
                        )

                        SettingsSwitchRow(
                            label = "Navigation Events",
                            description = if (isFullJsMode) "Capture SPA navigation" else "Capture URL changes",
                            checked = captureNavigationEvents,
                            onCheckedChange = { captureNavigationEvents = it },
                        )

                        if (isFullJsMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "JavaScript-Only Features",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = BitdriftColors.TextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )

                            SettingsSwitchRow(
                                label = "Console Logs",
                                description = "Capture console.log/warn/error",
                                checked = captureConsoleLogs,
                                onCheckedChange = { captureConsoleLogs = it },
                            )

                            SettingsSwitchRow(
                                label = "Web Vitals",
                                description = "Capture CLS, FCP, LCP, etc.",
                                checked = captureWebVitals,
                                onCheckedChange = { captureWebVitals = it },
                            )

                            SettingsSwitchRow(
                                label = "Long Tasks",
                                description = "Capture tasks >50ms",
                                checked = captureLongTasks,
                                onCheckedChange = { captureLongTasks = it },
                            )

                            SettingsSwitchRow(
                                label = "User Interactions",
                                description = "Capture clicks and taps",
                                checked = captureUserInteractions,
                                onCheckedChange = { captureUserInteractions = it },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { persistSettingsAndDismiss() },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitdriftColors.Primary,
                            contentColor = BitdriftColors.TextBright,
                        ),
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onDismiss() },
                ) {
                    Text(
                        text = "Cancel",
                        color = BitdriftColors.TextSecondary,
                    )
                }
            },
        )
    }

    @Composable
    fun InstrumentationModeSelector(
        selectedMode: WebViewInstrumentationMode,
        onModeSelected: (WebViewInstrumentationMode) -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BitdriftColors.Border, RoundedCornerShape(8.dp))
                .padding(8.dp),
        ) {
            ModeOption(
                mode = WebViewInstrumentationMode.NATIVE_ONLY,
                title = "Native Only (Safe)",
                description = "WebViewClient callbacks only. No JavaScript modification.",
                isSelected = selectedMode == WebViewInstrumentationMode.NATIVE_ONLY,
                onClick = { onModeSelected(WebViewInstrumentationMode.NATIVE_ONLY) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModeOption(
                mode = WebViewInstrumentationMode.JAVASCRIPT_BRIDGE,
                title = "Full with JavaScript",
                description = "Enables JavaScript for rich telemetry. Use with caution.",
                isSelected = selectedMode == WebViewInstrumentationMode.JAVASCRIPT_BRIDGE,
                onClick = { onModeSelected(WebViewInstrumentationMode.JAVASCRIPT_BRIDGE) },
                isWarning = true,
            )
        }
    }

    @Composable
    fun ModeOption(
        mode: WebViewInstrumentationMode,
        title: String,
        description: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        isWarning: Boolean = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .background(
                    if (isSelected) BitdriftColors.Primary.copy(alpha = 0.1f) else BitdriftColors.BackgroundPaper,
                    RoundedCornerShape(4.dp),
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = BitdriftColors.Primary,
                    unselectedColor = BitdriftColors.TextTertiary,
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isWarning) BitdriftColors.Warning else BitdriftColors.TextPrimary,
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = BitdriftColors.TextSecondary,
                )
            }
        }
    }

    @Composable
    fun SettingsSwitchRow(
        label: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    color = BitdriftColors.TextPrimary,
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = BitdriftColors.TextSecondary,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = BitdriftColors.TextBright,
                        checkedTrackColor = BitdriftColors.Primary,
                        uncheckedThumbColor = BitdriftColors.TextTertiary,
                        uncheckedTrackColor = BitdriftColors.Border,
                    ),
            )
        }
    }

    companion object {
        const val WEBVIEW_MONITORING_ENABLED_KEY = "webview_monitoring_enabled"
        const val WEBVIEW_INSTRUMENTATION_MODE_KEY = "webview_instrumentation_mode"
        const val WEBVIEW_ENABLE_CONSOLE_LOGS_KEY = "webview_capture_console_logs"
        const val WEBVIEW_ENABLE_ERRORS_KEY = "webview_capture_errors"
        const val WEBVIEW_ENABLE_NETWORK_REQUESTS_KEY = "webview_capture_network_requests"
        const val WEBVIEW_ENABLE_NAVIGATION_EVENTS_KEY = "webview_capture_navigation_events"
        const val WEBVIEW_ENABLE_PAGE_VIEWS_KEY = "webview_capture_page_views"
        const val WEBVIEW_ENABLE_WEB_VITALS_KEY = "webview_capture_web_vitals"
        const val WEBVIEW_ENABLE_LONG_TASKS_KEY = "webview_capture_long_tasks"
        const val WEBVIEW_ENABLE_USER_INTERACTIONS_KEY = "webview_capture_user_interactions"
    }
}
