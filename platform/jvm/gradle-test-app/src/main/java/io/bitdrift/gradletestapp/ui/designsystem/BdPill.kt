// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.ui.theme.BdShape
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/** Status chip. */
@Composable
fun BdStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = BitdriftColors.Primary,
    icon: ImageVector? = null,
) {
    Row(
        modifier =
            modifier
                .clip(BdShape.Pill)
                .background(color = tone.copy(alpha = 0.14f), shape = BdShape.Pill)
                .padding(horizontal = BdSpacing.md, vertical = BdSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(7.dp)
                        .clip(BdShape.Pill)
                        .background(tone),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tone,
        )
    }
}
