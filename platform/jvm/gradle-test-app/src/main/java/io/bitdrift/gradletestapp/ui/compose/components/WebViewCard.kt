// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.data.model.AppAction
import io.bitdrift.gradletestapp.data.model.NavigationAction
import io.bitdrift.gradletestapp.ui.designsystem.BdButtonSize
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.designsystem.BdTintedButton
import io.bitdrift.gradletestapp.ui.fragments.WebViewFragment
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebViewCard(
    onAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = stringResource(id = R.string.webview_testing),
        modifier = modifier,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BdSpacing.sm),
        ) {
            val automatic = WebViewFragment.WEBVIEW_DEMOS.getValue(WebViewFragment.FULL_AUTOMATIC)
            BdTintedButton(
                text = automatic.buttonName,
                accent = BitdriftColors.WebViewJavaScriptDisabled,
                onClick = { onAction(NavigationAction.NavigateToWebView(WebViewFragment.FULL_AUTOMATIC)) },
                size = BdButtonSize.Compact,
            )

            val manual = WebViewFragment.WEBVIEW_DEMOS.getValue(WebViewFragment.MANUAL)
            BdTintedButton(
                text = manual.buttonName,
                accent = BitdriftColors.WebViewJavaScriptEnabled,
                onClick = { onAction(NavigationAction.NavigateToWebView(WebViewFragment.MANUAL)) },
                size = BdButtonSize.Compact,
            )
        }
    }
}
