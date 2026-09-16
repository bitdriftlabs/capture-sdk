// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.NavigationAction
import io.bitdrift.gradletestapp.ui.compose.components.WebViewSettingsDialog.Companion.WEBVIEW_MONITORING_ENABLED_KEY
import io.bitdrift.gradletestapp.ui.designsystem.BdButtonSize
import io.bitdrift.gradletestapp.ui.designsystem.BdGroupLabel
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdStatusPill
import io.bitdrift.gradletestapp.ui.designsystem.BdTintedButton
import io.bitdrift.gradletestapp.ui.fragments.WebViewFragment
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationCard(
    onAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
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

    BdSectionCard(
        title = stringResource(id = R.string.navigation),
        modifier = modifier,
    ) {
        NavigationSection("Views") {
            BdSecondaryButton(
                text = "Compose",
                onClick = { onAction(NavigationAction.NavigateToCompose) },
                size = BdButtonSize.Compact,
            )
            BdSecondaryButton(
                text = "XML",
                onClick = { onAction(NavigationAction.NavigateToXml) },
                size = BdButtonSize.Compact,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(BdSpacing.md)) {
            BdGroupLabel("WebViews")

            BdStatusPill(
                text = if (webViewMonitoringEnabled) "WebView monitoring: Enabled" else "WebView monitoring: Disabled",
                tone = if (webViewMonitoringEnabled) BitdriftColors.Primary else BitdriftColors.Error,
            )

            WebViewGroup("Manual Instrumentation") {
                WebViewDemoButton(
                    WebViewFragment.MANUAL,
                    WebViewFragment.WEBVIEW_DEMOS.getValue(WebViewFragment.MANUAL),
                    onAction,
                )
            }

            WebViewGroup("Auto - WebViews With JavaScript") {
                WebViewDemoButton(
                    WebViewFragment.JAVASCRIPT_ENABLED,
                    WebViewFragment.WEBVIEW_DEMOS.getValue(WebViewFragment.JAVASCRIPT_ENABLED),
                    onAction,
                    accent = BitdriftColors.WebViewJavaScriptDisabled,
                )
            }

            WebViewGroup("Auto - WebViews Without JavaScript") {
                WebViewFragment.WEBVIEW_DEMOS
                    .filterKeys {
                        it !in setOf(WebViewFragment.MANUAL, WebViewFragment.JAVASCRIPT_ENABLED)
                    }.forEach { (key, demo) ->
                        WebViewDemoButton(
                            key,
                            demo,
                            onAction,
                            accent = BitdriftColors.WebViewJavaScriptEnabled,
                        )
                    }
            }
        }

        NavigationSection("Other") {
            BdSecondaryButton(
                text = stringResource(id = R.string.focus_matrix),
                onClick = { onAction(NavigationAction.NavigateToFocusMatrix) },
                size = BdButtonSize.Compact,
            )
            BdSecondaryButton(
                text = stringResource(id = R.string.navigate_to_modal_bottom_sheet),
                onClick = { onAction(NavigationAction.NavigateToDialogAndModals) },
                size = BdButtonSize.Compact,
            )
            BdSecondaryButton(
                text = "Invoke Service",
                onClick = { onAction(NavigationAction.InvokeService) },
                size = BdButtonSize.Compact,
            )
        }
    }
}

@Composable
private fun WebViewDemoButton(
    key: String,
    demo: WebViewFragment.DemoWebView,
    onAction: (AppAction) -> Unit,
    accent: Color =
        if (demo.hasJavaScript) {
            BitdriftColors.WebViewJavaScriptEnabled
        } else {
            BitdriftColors.WebViewJavaScriptDisabled
        },
) {
    BdTintedButton(
        text = demo.buttonName,
        accent = accent,
        onClick = { onAction(NavigationAction.NavigateToWebView(key)) },
        size = BdButtonSize.Compact,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebViewGroup(
    title: String,
    content: @Composable () -> Unit,
) = NavigationSection(title = title, content = content)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavigationSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BdSpacing.sm)) {
        BdGroupLabel(title)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BdSpacing.sm),
        ) {
            content()
        }
    }
}
