// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.ui.theme.BdEyebrowStyle
import io.bitdrift.gradletestapp.ui.theme.BdShape
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/** Flat card surface. */
@Composable
fun BdSurface(
    modifier: Modifier = Modifier,
    shape: Shape = BdShape.Card,
    contentPadding: PaddingValues = PaddingValues(BdSpacing.cardPadding),
    borderColor: Color = BitdriftColors.Border,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(BdSpacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(color = BitdriftColors.BackgroundPaper, shape = shape)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Titled card. */
@Composable
fun BdSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BdSurface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BdSpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BdSpacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BitdriftColors.TextBright,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BitdriftColors.TextTertiary,
                    )
                }
            }
            trailing?.invoke(this)
        }

        content()
    }
}

/** Sunken panel for read-only detail blocks. */
@Composable
fun BdInsetPanel(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(BdSpacing.xs),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BdShape.Inner)
                .background(color = BitdriftColors.BackgroundSunken, shape = BdShape.Inner)
                .padding(BdSpacing.md),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Uppercase group label with a hairline rule. */
@Composable
fun BdGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BitdriftColors.TextTertiary,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BdSpacing.sm),
    ) {
        Text(
            text = text.uppercase(),
            style = BdEyebrowStyle,
            color = color,
        )
        HorizontalDivider(color = BitdriftColors.Border)
    }
}

/** Key/value detail row. */
@Composable
fun BdDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = BitdriftColors.TextPrimary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BdSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BitdriftColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
        )
    }
}
