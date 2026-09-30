// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.bitdrift.gradletestapp.ui.designsystem.BdReadOnlyField
import io.bitdrift.gradletestapp.ui.designsystem.BdSecondaryButton
import io.bitdrift.gradletestapp.ui.designsystem.BdSectionCard

@Composable
fun EntityIdCard(
    entityIdValue: String,
    onClearEntityId: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BdSectionCard(
        title = "Entity ID",
        modifier = modifier,
    ) {
        BdReadOnlyField(
            label = "Entity ID",
            value = entityIdValue,
        )

        BdSecondaryButton(
            text = "Clear Entity ID",
            onClick = onClearEntityId,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
