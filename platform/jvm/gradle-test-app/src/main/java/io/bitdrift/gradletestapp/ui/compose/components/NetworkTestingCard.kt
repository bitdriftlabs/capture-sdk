// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.ui.designsystem.BdGroupLabel
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard
import io.bitdrift.gradletestapp.ui.theme.BdSpacing

/**
 * Network Testing Card component
 */
@Composable
fun NetworkTestingCard(
    instrumentationMode: String,
    onOkHttpRequest: () -> Unit,
    onOkHttpFailureBeforeResponseHeaders: () -> Unit,
    onDelayedOkHttpRequest: () -> Unit,
    onGraphQlRequest: () -> Unit,
    onRetrofitRequest: () -> Unit,
    onPreExistingW3cRequest: () -> Unit,
    onPreExistingB3SingleRequest: () -> Unit,
    onPreExistingB3MultiRequest: () -> Unit,
    onPreExistingDatadogRequest: () -> Unit,
    onLocalBackendAddToCartRequest: () -> Unit,
    onLocalBackendGetCartRequest: () -> Unit,
    onLocalBackendDeleteCartItemRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = "Network Testing",
        subtitle = "OkHttp instrumentation: $instrumentationMode",
        modifier = modifier,
    ) {
        ButtonRow(
            "OkHttp" to onOkHttpRequest,
            "Fail DNS" to onOkHttpFailureBeforeResponseHeaders,
            "Delay 3s" to onDelayedOkHttpRequest,
        )

        ButtonRow(
            "GraphQL" to onGraphQlRequest,
            "Retrofit" to onRetrofitRequest,
        )

        BdGroupLabel("Pre-existing Trace Headers")

        ButtonRow(
            "W3C" to onPreExistingW3cRequest,
            "B3 Single" to onPreExistingB3SingleRequest,
        )

        ButtonRow(
            "B3 Multi" to onPreExistingB3MultiRequest,
            "DD" to onPreExistingDatadogRequest,
            fillRemaining = true,
        )

        BdGroupLabel("Local Backend")

        ButtonRow(
            "Add" to onLocalBackendAddToCartRequest,
            "Get" to onLocalBackendGetCartRequest,
            "Delete" to onLocalBackendDeleteCartItemRequest,
        )
    }
}

@Composable
private fun ButtonRow(
    vararg actions: Pair<String, () -> Unit>,
    fillRemaining: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
    ) {
        actions.forEach { (label, action) ->
            BdSecondaryButton(
                text = label,
                onClick = action,
                modifier = Modifier.weight(1f),
            )
        }
        if (fillRemaining) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
