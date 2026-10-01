// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import android.view.View
import io.bitdrift.capture.replay.ReplayType
import io.bitdrift.capture.replay.WebViewReplaySnapshot
import kotlin.math.roundToInt

private const val ELEMENT_STRIDE = 5

private val webElementTypes: Map<Int, ReplayType> =
    listOf(
        ReplayType.Label,
        ReplayType.Button,
        ReplayType.TextInput,
        ReplayType.Image,
        ReplayType.View,
        ReplayType.BackgroundImage,
        ReplayType.SwitchOn,
        ReplayType.SwitchOff,
    ).associateBy { it.typeValue }

/**
 * Projects the CSS pixel rects of [snapshot] onto the screen bounds of [webView], dropping the
 * parts that fall outside of it.
 */
internal fun webViewReplayRects(
    webView: View,
    snapshot: WebViewReplaySnapshot,
): List<ReplayRect> {
    if (snapshot.viewportWidth <= 0 || webView.width <= 0 || webView.height <= 0) return emptyList()

    val scale = webView.width.toFloat() / snapshot.viewportWidth
    val location = IntArray(2)
    webView.getLocationOnScreen(location)
    val (originX, originY) = location
    val right = originX + webView.width
    val bottom = originY + webView.height

    val elements = snapshot.elements
    val rects = ArrayList<ReplayRect>(elements.size / ELEMENT_STRIDE)
    for (i in 0..elements.size - ELEMENT_STRIDE step ELEMENT_STRIDE) {
        val type = webElementTypes[elements[i]] ?: continue
        val left = (originX + elements[i + 1] * scale).roundToInt().coerceAtLeast(originX)
        val top = (originY + elements[i + 2] * scale).roundToInt().coerceAtLeast(originY)
        val elementRight = (originX + (elements[i + 1] + elements[i + 3]) * scale).roundToInt().coerceAtMost(right)
        val elementBottom = (originY + (elements[i + 2] + elements[i + 4]) * scale).roundToInt().coerceAtMost(bottom)
        if (elementRight <= left || elementBottom <= top) continue
        rects.add(ReplayRect(type, left, top, elementRight - left, elementBottom - top))
    }
    return rects
}
