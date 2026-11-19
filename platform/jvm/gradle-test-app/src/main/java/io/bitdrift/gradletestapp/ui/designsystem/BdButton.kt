// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.bitdrift.gradletestapp.ui.theme.BdShape
import io.bitdrift.gradletestapp.ui.theme.BdSpacing
import io.bitdrift.gradletestapp.ui.theme.BitdriftColors

/** Button footprint. [Compact] is for the chip-like buttons that sit in flow rows. */
enum class BdButtonSize {
    Regular,
    Compact,
}

/** Solid green action. */
@Composable
fun BdPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: BdButtonSize = BdButtonSize.Regular,
) {
    BdButtonBase(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = BitdriftColors.PrimaryFill,
        containerPressed = BitdriftColors.PrimaryFillPressed,
        contentColor = Color.White,
        size = size,
    ) {
        BdButtonContent(text = text, icon = icon)
    }
}

/** Neutral grey action. */
@Composable
fun BdSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: BdButtonSize = BdButtonSize.Regular,
) {
    BdButtonBase(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = BitdriftColors.SurfaceMuted,
        containerPressed = BitdriftColors.SurfaceMutedPressed,
        contentColor = BitdriftColors.TextMuted,
        size = size,
    ) {
        BdButtonContent(text = text, icon = icon)
    }
}

/** Solid button in an arbitrary accent, for the colour-coded destructive and WebView actions. */
@Composable
fun BdTintedButton(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: BdButtonSize = BdButtonSize.Regular,
) {
    BdButtonBase(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = accent,
        containerPressed = accent.darken(),
        contentColor = Color.White,
        size = size,
    ) {
        BdButtonContent(text = text, icon = icon)
    }
}

/** Square icon action. */
@Composable
fun BdIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = BitdriftColors.PrimaryFill,
    enabled: Boolean = true,
) {
    BdButtonBase(
        onClick = onClick,
        modifier = modifier.size(42.dp),
        enabled = enabled,
        container = accent,
        containerPressed = accent.darken(),
        contentColor = Color.White,
        size = BdButtonSize.Compact,
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RowScope.BdButtonContent(
    text: String,
    icon: ImageVector?,
) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
    Text(text = text, maxLines = 1, softWrap = false)
}

@Composable
private fun BdButtonBase(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    containerPressed: Color,
    contentColor: Color,
    size: BdButtonSize,
    shape: Shape = BdShape.Button,
    contentPadding: PaddingValues = defaultContentPadding(size),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = pressed && enabled

    val background by animateColorAsState(
        targetValue = if (active) containerPressed else container,
        animationSpec = tween(durationMillis = 120),
        label = "bdButtonBackground",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 0.985f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "bdButtonScale",
    )

    val minHeight = if (size == BdButtonSize.Regular) 44.dp else 36.dp

    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.alpha(if (enabled) 1f else 0.4f)
                .defaultMinSize(minHeight = minHeight)
                .clip(shape)
                .background(color = background, shape = shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    role = Role.Button,
                    onClick = onClick,
                ).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(BdSpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

private fun defaultContentPadding(size: BdButtonSize): PaddingValues =
    when (size) {
        BdButtonSize.Regular -> PaddingValues(horizontal = 20.dp, vertical = 10.dp)
        BdButtonSize.Compact -> PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    }

/** Pressed-state variant of a fill. */
private fun Color.darken(factor: Float = 0.86f): Color =
    Color(
        red = red * factor,
        green = green * factor,
        blue = blue * factor,
        alpha = alpha,
    )
