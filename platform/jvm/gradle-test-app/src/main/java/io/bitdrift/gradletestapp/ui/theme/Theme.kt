// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors: ColorScheme =
    darkColorScheme(
        primary = BitdriftColors.Primary,
        onPrimary = BitdriftColors.TextBright,
        background = BitdriftColors.Background,
        onBackground = BitdriftColors.TextPrimary,
        surface = BitdriftColors.BackgroundPaper,
        onSurface = BitdriftColors.TextPrimary,
        secondary = BitdriftColors.TextSecondary,
        onSecondary = BitdriftColors.TextBright,
        error = Color(0xFFCF6679),
    )

@Composable
fun BitdriftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
