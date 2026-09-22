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
import io.bitdrift.gradletestapp.ui.designsystem.BdPrimaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard

/**
 * Span Testing Card component
 */
@Composable
fun SpanTestingCard(
    onStartSpan: () -> Unit,
    onEndSpan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = "Span Testing",
        modifier = modifier,
    ) {
        BdPrimaryButton(
            text = "Start Span",
            onClick = onStartSpan,
            modifier = Modifier.fillMaxWidth(),
        )

        BdSecondaryButton(
            text = "End Span",
            onClick = onEndSpan,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
