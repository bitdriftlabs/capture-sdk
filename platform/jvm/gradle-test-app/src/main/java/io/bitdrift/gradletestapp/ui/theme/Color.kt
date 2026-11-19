// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.theme

import androidx.compose.ui.graphics.Color

object BitdriftColors {
    // Accent: status text, icons, selected states
    val Primary = Color(0xFF22C55E)
    val PrimaryBright = Color(0xFF4ADE80)
    val PrimaryDeep = Color(0xFF16A34A)

    // Primary action fill
    val PrimaryFill = Color(0xFF3A9A61)
    val PrimaryFillPressed = Color(0xFF318452)

    // Neutral action fill
    val SurfaceMuted = Color(0xFF272E3C)
    val SurfaceMutedPressed = Color(0xFF323A4B)

    // Canvas
    val Background = Color(0xFF0B0E13)
    val BackgroundPaper = Color(0xFF1C2128)
    val BackgroundElevated = Color(0xFF232935)
    val BackgroundSunken = Color(0xFF171B22)

    // Text
    val TextPrimary = Color(0xCCDFE9F5)
    val TextSecondary = Color(0xFF557CA6)
    val TextTertiary = Color(0xFF6B7186)
    val TextMuted = Color(0xFF959FB2)
    val TextBright = Color(0xFFF1F6EE)

    // Lines
    val Border = Color(0xFF262D3A)
    val BorderStrong = Color(0xFF39414F)

    // Semantic
    val Error = Color(0xFFD5566B)
    val Warning = Color(0xFFD99A34)
    val WebViewJavaScriptEnabled = Color(0xFFC26A2B)
    val WebViewJavaScriptDisabled = Color(0xFF3D6FC4)
    val CrashNative = Color(0xFFC0405A)
    val CrashJvm = Color(0xFFC08A2E)
    val CrashAnr = Color(0xFF7059C0)
}
