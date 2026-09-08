// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.NavigationAction
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_MONITORING_ENABLED_KEY
import io.bitdrift.gradletestapp.ui.fragments.WebViewFragment
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationCard(onAction: (AppAction) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var webViewMonitoringEnabled by remember {
        mutableStateOf(preferences.getBoolean(WEBVIEW_MONITORING_ENABLED_KEY, false))
    }
    DisposableEffect(preferences) {
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key == WEBVIEW_MONITORING_ENABLED_KEY) {
                    webViewMonitoringEnabled = sharedPreferences.getBoolean(key, false)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = BitdriftColors.BackgroundPaper),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(width = 1.dp, color = BitdriftColors.Border.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.navigation),
                style = MaterialTheme.typography.titleMedium,
                color = BitdriftColors.TextPrimary,
            )

            NavigationSection("Views") {
                OutlinedButton(
                    onClick = { onAction(NavigationAction.NavigateToCompose) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text("Compose", maxLines = 1, softWrap = false) }

                OutlinedButton(
                    onClick = { onAction(NavigationAction.NavigateToXml) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text("XML", maxLines = 1, softWrap = false) }
            }

            NavigationSection("WebViews") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text =
                            if (webViewMonitoringEnabled) {
                                "WebView monitoring: Enabled"
                            } else {
                                "WebView monitoring: Disabled"
                            },
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (webViewMonitoringEnabled) {
                                BitdriftColors.Primary
                            } else {
                                BitdriftColors.Error
                            },
                    )

                    WebViewGroup("Manual instrumentation - (No plugin required)") {
                        WebViewDemoButton(
                            WebViewFragment.MANUAL,
                            WebViewFragment.WEBVIEW_DEMOS.getValue(WebViewFragment.MANUAL),
                            onAction,
                        )
                    }

                    WebViewGroup("Auto - With JavaScript enabled (Only when plugin is applied)") {
                        WebViewDemoButton(
                            WebViewFragment.JAVASCRIPT_ENABLED,
                            WebViewFragment.WEBVIEW_DEMOS.getValue(WebViewFragment.JAVASCRIPT_ENABLED),
                            onAction,
                        )
                    }

                    WebViewGroup("Auto - With JavaScript disable (Only when plugin is applied)") {
                        WebViewFragment.WEBVIEW_DEMOS
                            .filterKeys {
                                it !in setOf(WebViewFragment.MANUAL, WebViewFragment.JAVASCRIPT_ENABLED)
                            }.forEach { (key, demo) ->
                                WebViewDemoButton(key, demo, onAction)
                            }
                    }
                }
            }

            NavigationSection("Other") {
                OutlinedButton(
                    onClick = { onAction(NavigationAction.NavigateToFocusMatrix) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text(stringResource(id = R.string.focus_matrix), maxLines = 1, softWrap = false) }

                OutlinedButton(
                    onClick = { onAction(NavigationAction.NavigateToDialogAndModals) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text(stringResource(id = R.string.navigate_to_modal_bottom_sheet), maxLines = 1, softWrap = false) }

                OutlinedButton(
                    onClick = { onAction(NavigationAction.InvokeService) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BitdriftColors.TextPrimary),
                ) { Text("Invoke Service", maxLines = 1, softWrap = false) }

            }
        }
    }
}

@Composable
private fun WebViewDemoButton(
    key: String,
    demo: WebViewFragment.DemoWebView,
    onAction: (AppAction) -> Unit,
) {
    Button(
        onClick = { onAction(NavigationAction.NavigateToWebView(key)) },
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (demo.hasJavaScript) {
                        BitdriftColors.WebViewJavaScriptEnabled
                    } else {
                        BitdriftColors.WebViewJavaScriptDisabled
                    },
                contentColor = BitdriftColors.TextBright,
            ),
    ) {
        Text(demo.buttonName, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebViewGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = BitdriftColors.TextPrimary,
        )
        HorizontalDivider(color = BitdriftColors.Border)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavigationSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = BitdriftColors.TextSecondary,
        )
        HorizontalDivider(color = BitdriftColors.Border)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}
