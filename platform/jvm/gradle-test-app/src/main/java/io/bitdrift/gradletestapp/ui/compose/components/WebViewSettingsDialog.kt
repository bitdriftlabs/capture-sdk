// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

    @Composable
    fun WebViewSettingsDialog(
        onDismiss: () -> Unit,
        sharedPreferences: SharedPreferences,
    ) {
        var monitoringEnabled by remember {
            mutableStateOf(sharedPreferences.getBoolean(WEBVIEW_MONITORING_ENABLED_KEY, false))
        }
        var captureConsoleLog by remember {
            mutableStateOf(sharedPreferences.getBoolean(WEBVIEW_CAPTURE_CONSOLE_LOG_KEY, true))
        }

        fun persistSettingsAndDismiss() {
            with(sharedPreferences.edit()) {
                putBoolean(WEBVIEW_MONITORING_ENABLED_KEY, monitoringEnabled)
                putBoolean(WEBVIEW_CAPTURE_CONSOLE_LOG_KEY, captureConsoleLog)
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
                            text = "Options",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BitdriftColors.TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        SettingsSwitchRow(
                            label = "Capture Console Log",
                            description = "Capture JS console.log/warn/error",
                            checked = captureConsoleLog,
                            onCheckedChange = { captureConsoleLog = it },
                        )
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
        const val WEBVIEW_CAPTURE_CONSOLE_LOG_KEY = "webview_capture_console_log"
    }
}
