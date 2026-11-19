// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.bitdrift.gradletestapp.ui.theme.BdShape
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

data class BdNavItem(
    val label: String,
    val icon: ImageVector,
)

/** Floating capsule navigation bar. */
@Composable
fun BdFloatingNavBar(
    items: List<BdNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = BdSpacing.md, vertical = BdSpacing.sm)
                .shadow(elevation = 16.dp, shape = BdShape.Pill, clip = false)
                .clip(BdShape.Pill)
                .background(color = BitdriftColors.BackgroundElevated, shape = BdShape.Pill)
                .border(width = 1.dp, color = BitdriftColors.Border, shape = BdShape.Pill)
                .padding(horizontal = BdSpacing.xs, vertical = BdSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BdSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavBarItem(
                item = item,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RowScope.NavBarItem(
    item: BdNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val chipColor by animateColorAsState(
        targetValue =
            if (selected) {
                BitdriftColors.Primary.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            },
        animationSpec = tween(durationMillis = 220),
        label = "bdNavChip",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) BitdriftColors.PrimaryBright else BitdriftColors.TextMuted,
        animationSpec = tween(durationMillis = 220),
        label = "bdNavContent",
    )

    Column(
        modifier =
            Modifier
                .weight(1f)
                .clip(BdShape.Pill)
                .background(color = chipColor, shape = BdShape.Pill)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = BitdriftColors.Primary),
                    role = Role.Tab,
                    onClick = onClick,
                ).padding(vertical = BdSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BdSpacing.xxs),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            color = contentColor,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
